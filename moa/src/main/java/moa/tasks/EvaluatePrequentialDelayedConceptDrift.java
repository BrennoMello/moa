package moa.tasks;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.Classifier;
import moa.core.*;
import moa.evaluation.LearningEvaluation;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.evaluation.preview.LearningCurve;
import moa.learners.Learner;
import moa.options.ClassOption;
import moa.streams.ExampleStream;
import moa.streams.InstanceStreamConceptDrift;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class EvaluatePrequentialDelayedConceptDrift extends ConceptDriftMainTask {


    public String getPurposeString() {
        return "Evaluates a drift detector on a delayed stream by testing and only"
                + " training with the example after k other examples (delayed labeling).";
    }

    public ClassOption learnerOption = new ClassOption("learner", 'l',
            "Change detector to train.", Learner.class, "moa.learners.ChangeDetectorGeneratorsLearner");


    public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to learn from.", ExampleStream.class,
            "generators.RandomTreeGenerator");

    public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
            "Classification performance evaluation method.",
            LearningPerformanceEvaluator.class,
            "BasicPartiallyLabeledConceptDriftPerformanceEvaluator");

    public IntOption delayLengthOption = new IntOption("delay", 'k',
            "Number of instances before test instance is used for training",
            1000, 1, Integer.MAX_VALUE);

    public IntOption initialWindowSizeOption = new IntOption("initialTrainingWindow", 'p',
            "Number of instances used for training in the beginning of the stream.",
            1000, 0, Integer.MAX_VALUE);

    public FlagOption trainOnInitialWindowOption = new FlagOption("trainOnInitialWindow", 'm',
            "Whether to train or not using instances in the initial window.");

    public FlagOption trainInBatches = new FlagOption("trainInBatches", 'b',
            "If set training will not be interleaved with testing. ");

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

    public IntOption seedOption = new IntOption("seed", 'z',
            "seed of experiment", 0);

    protected LinkedList<Example> trainInstances;

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
        //Learner learner = (Learner) getPreparedClassOption(this.learnerOption);
        //ExampleStream stream = (ExampleStream) getPreparedClassOption(this.streamOption);
        LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(this.evaluatorOption);
        LearningCurve learningCurve = new LearningCurve("learning evaluation instances");
        Classifier learner = (Classifier) getPreparedClassOption(this.learnerOption);
        InstanceStreamConceptDrift stream = (InstanceStreamConceptDrift) getPreparedClassOption(this.streamOption);

        Random rnd = new Random();
        rnd.setSeed(this.seedOption.getValue());
        this.trainInstances = new LinkedList<Example>();

        List<Integer> listDriftposition = stream.getDriftPositions();
        List<Integer> listDriftWidths = stream.getDriftWidths();

        learner.setModelContext(stream.getHeader());
        int maxInstances = this.instanceLimitOption.getValue();
        long instancesProcessed = 0;
        int maxSeconds = this.timeLimitOption.getValue();
        int secondsElapsed = 0;
        monitor.setCurrentActivity("Evaluating learner...", -1.0);

        boolean preciseCPUTiming = TimingUtils.enablePreciseTiming();
        long evaluateStartTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
        long lastEvaluateStartTime = evaluateStartTime;
        double RAMHours = 0.0;

        while (stream.hasMoreInstances()
                && ((maxInstances < 0) || (instancesProcessed < maxInstances))
                && ((maxSeconds < 0) || (secondsElapsed < maxSeconds))) {

            instancesProcessed++;
            Example currentInst = stream.nextInstance();

            int groundTruth = 0;
            if(listDriftposition.size()>0)
                groundTruth = findGroundTruth(listDriftposition, listDriftWidths, instancesProcessed);
            System.out.println("Ground Truth: " + groundTruth + " instancesProcessed: " + instancesProcessed);


            if (instancesProcessed <= this.initialWindowSizeOption.getValue()) {
                if (this.trainOnInitialWindowOption.isSet()) {
                    learner.trainOnInstance(currentInst);
                } else if ((this.initialWindowSizeOption.getValue() - instancesProcessed) < this.delayLengthOption.getValue()) {
                    this.trainInstances.addLast(currentInst);
                }
            } else {
                this.trainInstances.addLast(currentInst);

                if (this.delayLengthOption.getValue() < this.trainInstances.size()) {
                    if (this.trainInBatches.isSet()) {
                        // Do not train on the latest instance, otherwise
                        // it would train on k+1 instances
                        while (this.trainInstances.size() > 1) {
                            Example trainInst = this.trainInstances.removeFirst();
                            learner.trainOnInstance(trainInst);
                        }
                    } else {
                        Example trainInst = this.trainInstances.removeFirst();
                        learner.trainOnInstance(trainInst);
                    }
                }

                // Remove class label from test instances.
                Instance testInstance = ((Instance) currentInst.getData()).copy();
                Example testInst = new InstanceExample(testInstance);
                testInstance.setMissing(testInstance.classAttribute());
                testInstance.setClassValue(0.0);

                double[] prediction = learner.getVotesForInstance(testInst);
                // reinstate the testInstance as it is used in evaluator.addResult
                testInstance = ((Instance) currentInst.getData()).copy();
                testInst = new InstanceExample(testInstance);

                //evaluator.addResult(testInst, prediction);
                evaluator.addResult(testInst, groundTruth, prediction);

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
        }

        return learningCurve;
    }


    @Override
    public Class<?> getTaskResultType() {
        return LearningCurve.class;
    }
}