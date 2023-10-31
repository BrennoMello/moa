package moa.tasks;

import com.github.javacliparser.FileOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import moa.classifiers.AbstractClassifier;
import moa.classifiers.Classifier;
import moa.classifiers.MultiClassClassifier;
import moa.core.Example;
import moa.core.Measurement;
import moa.core.ObjectRepository;
import moa.core.TimingUtils;
import moa.evaluation.LearningEvaluation;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.evaluation.preview.LearningCurve;
import moa.learners.Learner;
import moa.options.ClassOption;
import moa.streams.ExampleStream;
import moa.streams.InstanceStreamConceptDrift;

import java.util.List;
import java.util.Random;

public class EvaluatePrequentialPartiallyLabeledClassifierConcepDrift extends ConceptDriftMainTask {

    private static final long serialVersionUID = 1L;

    public String getPurposeString() {
        return "Evaluates a classifier on a stream by testing then training with each example in sequence and partially labeled on stream.";
    }

    public ClassOption learnerOption = new ClassOption("learner", 'l',
            "Change detector to train.", AbstractClassifier.class, "moa.classifiers.drift.DetectionConceptDriftMethodClassifier");

    public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to learn from.", ExampleStream.class,
            "generators.RandomTreeGenerator");

    public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
            "Classification performance evaluation method.",
            LearningPerformanceEvaluator.class,
            "BasicPartiallyLabeledConceptDriftPerformanceEvaluator");

    public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
            "Maximum number of instances to test/train on  (-1 = no limit).",
            100000000, -1, Integer.MAX_VALUE);

    public IntOption timeLimitOption = new IntOption("timeLimit", 't',
            "Maximum number of seconds to test/train for (-1 = no limit).", -1,
            -1, Integer.MAX_VALUE);

    public IntOption sampleFrequencyOption = new IntOption("sampleFrequency",
            'f',
            "How many instances between samples of the learning performance.",
            100000, 0, Integer.MAX_VALUE);

    public IntOption memCheckFrequencyOption = new IntOption(
            "memCheckFrequency", 'q',
            "How many instances between memory bound checks.", 100000, 0,
            Integer.MAX_VALUE);

    public IntOption percentageUnlabelledOption = new IntOption("percentageUnlabelled", 'p',
            "Percentage of amount instances unlabelled.", 0);

    public IntOption seedOption = new IntOption("seed", 'z',
            "seed of experiment", 0);
    @Override
    public Class<?> getTaskResultType() {
        return LearningCurve.class;
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
        Random rnd = new Random();
        rnd.setSeed(this.seedOption.getValue());

        Classifier learner = (Classifier) getPreparedClassOption(this.learnerOption);
        InstanceStreamConceptDrift stream = (InstanceStreamConceptDrift) getPreparedClassOption(this.streamOption);
        LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(this.evaluatorOption);

        LearningCurve learningCurve = new LearningCurve("learning evaluation instances");

        learner.setModelContext(stream.getHeader());

        List<Integer> listDriftPosition = stream.getDriftPositions();
        List<Integer> listDriftWidths = stream.getDriftWidths();

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

            Example trainInst = (Example) stream.nextInstance();

            if(rnd.nextInt(100) >= percentageUnlabelled){
                learner.trainOnInstance(trainInst);
            }

            double[] prediction = learner.getVotesForInstance(trainInst);

            //System.out.println("is Change: " + prediction[0] + " Warning Zone: " + prediction[1] + " delay: " + prediction[2] + " estimation: " + prediction[3]);
            int groundTruth = 0;
            if(listDriftPosition.size()>0)
                groundTruth = findGroundTruth(listDriftPosition, listDriftWidths, instancesProcessed);
            //System.out.println("Ground Truth: " + groundTruth + " instancesProcessed: " + instancesProcessed);

            evaluator.addResult(trainInst, groundTruth, prediction);
            instancesProcessed++;

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

}
