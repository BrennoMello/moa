package moa.learners;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.AbstractClassifier;
import moa.classifiers.MultiClassClassifier;
import moa.classifiers.core.driftdetection.ChangeDetector;
import moa.core.Measurement;
import moa.core.Utils;
import moa.options.ClassOption;
import java.util.LinkedList;

public class StuddLearner extends AbstractClassifier {

    private static final long serialVersionUID = -3518369648142099718L;

    public ClassOption baseLearnerOption = new ClassOption("baseLearner", 'b',
            "Base learner to train.", MultiClassClassifier.class, "moa.classifiers.meta.AdaptiveRandomForest");

    public ClassOption studentLearnerOption = new ClassOption("studentLearner", 's',
            "Student learner to train.", MultiClassClassifier.class, "moa.classifiers.meta.AdaptiveRandomForest");


    public ClassOption driftDetectionMethodOption = new ClassOption("driftDetectionMethod", 'd',
            "Drift detection method to use.", ChangeDetector.class, "DDM");

    public IntOption sizeBatchTrainOption = new IntOption("width",
            'w', "Size of Window", 500);

    private LinkedList<Instance> listBatchInstances;
    private LinkedList<Instance> listBufferInstances;
    private AbstractClassifier  baseLearner;
    private AbstractClassifier  studentLearner;
    private ChangeDetector studentChangeDetector;
    private int sizeBatchTrain;
    private int instacesProcessed;
    private boolean  isTrainingStudent;

    @Override
    public void resetLearningImpl() {
        this.baseLearner = (AbstractClassifier) getPreparedClassOption(this.baseLearnerOption);
        this.studentLearner = (AbstractClassifier)  getPreparedClassOption(this.studentLearnerOption);
        this.studentChangeDetector = (ChangeDetector) getPreparedClassOption(this.driftDetectionMethodOption);
        this.listBatchInstances = new LinkedList<>();
        this.listBufferInstances = new LinkedList<>();
        this.sizeBatchTrain = this.sizeBatchTrainOption.getValue();
        this.isTrainingStudent = false;
    }

    @Override
    public void trainOnInstanceImpl(Instance inst) {
        this.instacesProcessed++;

        if(this.instacesProcessed < this.sizeBatchTrain) {

            this.baseLearner.trainOnInstance(inst);
            this.listBatchInstances.add(inst);

        } else if(this.isTrainingStudent == false) {

            for (Instance instance : this.listBatchInstances) {
                double [] prediction = this.baseLearner.getVotesForInstance(instance);
                Instance copyInstance = instance.copy();
                copyInstance.setClassValue(Utils.maxIndex(prediction));
                this.studentLearner.trainOnInstance(copyInstance);
            }

            this.isTrainingStudent = true;
            System.out.println("Finished train student");
        }else{


            double [] predictionBase = this.baseLearner.getVotesForInstance(inst);
            Instance copyInstance = inst.copy();
            int predictBase = Utils.maxIndex(predictionBase);
            copyInstance.setClassValue(predictBase);
            double [] predictionStudent = this.studentLearner.getVotesForInstance(copyInstance);
            int predictStudent = Utils.maxIndex(predictionStudent);
            int studentError = predictStudent != predictBase ? 1 : 0;
            this.studentChangeDetector.input(studentError);
            this.listBufferInstances.add(copyInstance);
            //System.out.println("student Error " + studentError);

            if(this.studentChangeDetector.getChange()){
                System.out.println("Change detected");

                for (int i=0; i < this.sizeBatchTrain; i++){
                    Instance instance = this.listBatchInstances.get(i);
                    this.baseLearner.trainOnInstance(instance);
                }

                for (Instance instance : this.listBufferInstances){
                    double [] predictionBaseUpdate = this.baseLearner.getVotesForInstance(instance);
                    Instance copyInstanceUpdate = inst.copy();
                    int predictBaseUpdate = Utils.maxIndex(predictionBaseUpdate);
                    copyInstanceUpdate.setClassValue(predictBaseUpdate);
                    this.studentLearner.trainOnInstance(copyInstanceUpdate);
                }

                this.listBufferInstances.clear();
            }

        }

    }

    @Override
    public double[] getVotesForInstance(Instance inst) {
        return this.studentChangeDetector.getOutput();
    }

    @Override
    public boolean isRandomizable() {
        return false;
    }


    @Override
    public void getDescription(StringBuilder sb, int indent) {

    }


    @Override
    protected Measurement[] getModelMeasurementsImpl() {
        return new Measurement[0];
    }

    @Override
    public void getModelDescription(StringBuilder out, int indent) {

    }



}
