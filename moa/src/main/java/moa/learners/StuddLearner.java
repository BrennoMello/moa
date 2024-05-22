package moa.learners;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.AbstractClassifier;
import moa.classifiers.core.driftdetection.ChangeDetector;
import moa.core.Measurement;
import moa.core.Utils;
import moa.options.ClassOption;
import java.util.LinkedList;

public class StuddLearner extends AbstractClassifier {

    private static final long serialVersionUID = -3518369648142099718L;

    public ClassOption baseLearnerOption = new ClassOption("baseLearner", 'b',
            "Base learner to train.", AbstractClassifier.class, "moa.classifiers.meta.AdaptiveRandomForest");

    public ClassOption studentLearnerOption = new ClassOption("studentLearner", 's',
            "Student learner to train.", AbstractClassifier.class, "moa.classifiers.meta.AdaptiveRandomForest");


    public ClassOption driftDetectionMethodOption = new ClassOption("driftDetectionMethod", 'd',
            "Drift detection method to use.", ChangeDetector.class, "DDM");

    public IntOption sizeBatchTrainOption = new IntOption("width",
            'w', "Size of Window", 500);

    private LinkedList<Instance> listBatchInstances;

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
            //System.out.println("Finished train student");
        }else{

            this.listBatchInstances.add(inst);
            double [] predictionBase = this.baseLearner.getVotesForInstance(inst);
            int predictBase = Utils.maxIndex(predictionBase);
            double [] predictionStudent = this.studentLearner.getVotesForInstance(inst);
            int predictStudent = Utils.maxIndex(predictionStudent);
            int studentError = predictStudent != predictBase ? 1 : 0;

            //if(studentError == 1)
            //    System.out.println("student Error " + studentError);

            this.studentChangeDetector.input(studentError);

            if(this.studentChangeDetector.getChange()){
                //System.out.println("Change detected");
                this.studentLearner.resetLearning();
                this.baseLearner.resetLearning();

                for (int i=this.listBatchInstances.size() - this.sizeBatchTrain; i < this.listBatchInstances.size(); i++){
                    Instance instance = this.listBatchInstances.get(i);
                    this.baseLearner.trainOnInstance(instance);
                }

                for (int i=this.listBatchInstances.size() - this.sizeBatchTrain; i < this.listBatchInstances.size(); i++){
                    Instance instance = this.listBatchInstances.get(i);
                    double [] predictionBaseUpdate = this.baseLearner.getVotesForInstance(instance);
                    Instance copyInstanceUpdate = inst.copy();
                    int predictBaseUpdate = Utils.maxIndex(predictionBaseUpdate);
                    copyInstanceUpdate.setClassValue(predictBaseUpdate);
                    this.studentLearner.trainOnInstance(copyInstanceUpdate);
                }

                // Train with all buffer data
                /*
                for (Instance instance : this.listBatchInstances){
                    double [] predictionBaseUpdate = this.baseLearner.getVotesForInstance(instance);
                    Instance copyInstanceUpdate = inst.copy();
                    int predictBaseUpdate = Utils.maxIndex(predictionBaseUpdate);
                    copyInstanceUpdate.setClassValue(predictBaseUpdate);
                    this.studentLearner.trainOnInstance(copyInstanceUpdate);
                }
                */
                //System.out.println("Finished retrain student");
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
