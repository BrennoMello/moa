package moa.tasks;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.Classifier;
import moa.core.*;
import moa.evaluation.LearningEvaluation;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.evaluation.preview.LearningCurve;
import moa.learners.Learner;
import moa.learners.StuddLearner;
import moa.options.ClassOption;
import moa.streams.ExampleStream;
import moa.streams.InstanceStreamConceptDrift;
import moa.streams.generators.cd.ConceptDriftGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EvaluatePartiallyLabeledConceptDrift extends ConceptDriftMainTask{

    private static final long serialVersionUID = 1L;

    public ClassOption learnerOption = new ClassOption("learner", 'l',
            "Change detector to train.", Learner.class, "moa.learners.ChangeDetectorGeneratorsLearner");

   public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to learn from.", ExampleStream.class,
            "generators.RandomTreeGenerator");

    /*
   public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to learn from.", ConceptDriftGenerator.class,
            "GradualChangeGenerator");
   */


    public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
            "Classification performance evaluation method.",
            LearningPerformanceEvaluator.class,
            "BasicPartiallyLabeledConceptDriftPerformanceEvaluator");

    public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
            "Maximum number of instances to test/train on  (-1 = no limit).",
            1000, -1, Integer.MAX_VALUE);

    public IntOption sampleFrequencyOption = new IntOption("sampleFrequency",
            'f',
            "How many instances between samples of the learning performance.",
            10, 0, Integer.MAX_VALUE);

    public IntOption timeLimitOption = new IntOption("timeLimit", 't',
            "Maximum number of seconds to test/train for (-1 = no limit).", -1,
            -1, Integer.MAX_VALUE);
    public IntOption seedOption = new IntOption("seed", 'z',
            "seed of experiment", 0);

    public IntOption percentageUnlabelledOption = new IntOption("percentageUnlabelled", 'p',
            "Percentage of amount instances unlabelled.", 0);
    @Override
    public String getPurposeString() {
        return "Evaluates a concept drift detector on a stream by testing then training with each example in sequence.";
    }

    //TODO: Change this method to a more general one
    public int findGroundTruthOld(List<Integer> listDriftPosition, List<Integer> listDriftWidths, long instancesProcessed){
        int groundTruth = 0;
        int avgDriftWidthOld = listDriftWidths.get(0)/2;
        int sumDriftPoints = listDriftPosition.get(0) + avgDriftWidthOld;
        for (int i = 0; i < listDriftPosition.size(); i++){
            //System.out.println("instancesProcessed: " + instancesProcessed + " sumDriftPoints: " + sumDriftPoints);
            if(instancesProcessed == sumDriftPoints){
                groundTruth = 1;
            }
            sumDriftPoints += (listDriftPosition.get(i) - avgDriftWidthOld) + listDriftWidths.get(i)/2;
            avgDriftWidthOld = listDriftWidths.get(i)/2;
        }
        return groundTruth;
    }

    public int findGroundTruth(List<Integer> listDriftPosition, List<Integer> listDriftWidths, long instancesProcessed){
        int groundTruth = 0;
        int sumDriftPoints = listDriftPosition.get(0);
        for (int i = 0; i < listDriftPosition.size(); i++){
            //System.out.println("instancesProcessed: " + instancesProcessed + " sumDriftPoints: " + sumDriftPoints);
            if(instancesProcessed == sumDriftPoints){
                groundTruth = 1;
            }
            sumDriftPoints += listDriftPosition.get(i);
        }
        return groundTruth;
    }

    @Override
    protected Object doMainTask(TaskMonitor monitor, ObjectRepository repository) {
        Classifier learner = (Classifier) getPreparedClassOption(this.learnerOption);
        InstanceStreamConceptDrift stream = (InstanceStreamConceptDrift) getPreparedClassOption(this.streamOption);
        //ConceptDriftGenerator stream = (ConceptDriftGenerator) getPreparedClassOption(this.streamOption);

        Random rnd = new Random();
        rnd.setSeed(this.seedOption.getValue());

        LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(this.evaluatorOption);

        LearningCurve learningCurve = new LearningCurve("learning evaluation instances");
        learner.setModelContext(stream.getHeader());
        //this.setEventsList(stream.getEventsList());

        List<Integer> listDriftposition = stream.getDriftPositions();
        List<Integer> listDriftWidths = stream.getDriftWidths();

        //List<Integer> listDriftposition = new ArrayList<>();
        //List<Integer> listDriftWidths = new ArrayList<>();

        int maxInstances = this.instanceLimitOption.getValue();
        long instancesProcessed = 0;
        int maxSeconds = this.timeLimitOption.getValue();
        int secondsElapsed = 0;
        int percentageUnlabelled = this.percentageUnlabelledOption.getValue();
        monitor.setCurrentActivity("Evaluating learner...", -1.0);

        boolean preciseCPUTiming = TimingUtils.enablePreciseTiming();
        long evaluateStartTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
        long lastEvaluateStartTime = evaluateStartTime;
        double RAMHours = 0.0;

        while (stream.hasMoreInstances()
                && ((maxInstances < 0) || (instancesProcessed < maxInstances))
                && ((maxSeconds < 0) || (secondsElapsed < maxSeconds))) {

            Example trainInst = (Example)  stream.nextInstance();

            if(rnd.nextInt(100) >= percentageUnlabelled){
                learner.trainOnInstance(trainInst);
            }

            double[] prediction = learner.getVotesForInstance(trainInst);

            //System.out.println("is Change: " + prediction[0] + " Warning Zone: " + prediction[1] + " delay: " + prediction[2] + " estimation: " + prediction[3]);
            int groundTruth = 0;
            if(listDriftposition.size()>0)
                groundTruth = findGroundTruth(listDriftposition, listDriftWidths, instancesProcessed);
            //System.out.println("Ground Truth: " + groundTruth + " instancesProcessed: " + instancesProcessed);

            evaluator.addResult(trainInst, groundTruth, prediction);
            instancesProcessed++;

            /*
            for (int i = 0; i < prediction.length; i++) {
                System.out.print("Prediction: " + prediction[i] + " ");
            }
            System.out.println();

            if (prediction[0] == 1){ //Change detected
                this.getEventsList().add(new ClusterEvent(this, instancesProcessed, "Detected Change", "Drift"));
            }
            */

            if (instancesProcessed % this.sampleFrequencyOption.getValue() == 0
                    || stream.hasMoreInstances() == false) {

                long evaluateTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
                double time = TimingUtils.nanoTimeToSeconds(evaluateTime - evaluateStartTime);
                double timeIncrement = TimingUtils.nanoTimeToSeconds(evaluateTime - lastEvaluateStartTime);
                double RAMHoursIncrement = learner.measureByteSize() / (1024.0 * 1024.0 * 1024.0); //GBs
                RAMHoursIncrement *= (timeIncrement / 3600.0); //Hours
                RAMHours += RAMHoursIncrement;
                lastEvaluateStartTime = evaluateTime;
                learningCurve.insertEntry(new LearningEvaluation(
                        new Measurement[]{
                                new Measurement(
                                        "learning evaluation instances",
                                        instancesProcessed),
                                new Measurement(
                                        "evaluation time ("
                                                + (preciseCPUTiming ? "cpu "
                                                : "") + "seconds)",
                                        time),
                                new Measurement(
                                        "model cost (RAM-Hours)",
                                        RAMHours)
                        },
                        evaluator, learner));
            }

        }
        return learningCurve;
    }

    @Override
    public Class<?> getTaskResultType() {
        return LearningCurve.class;
    }
}
