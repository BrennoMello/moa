package moa.tasks;

import com.github.javacliparser.FileOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
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
import moa.tasks.ConceptDriftMainTask;
import moa.tasks.TaskMonitor;

import java.util.Random;

public class EvaluatePrequentialPartiallyLabelled extends ConceptDriftMainTask {

    public String getPurposeString() {
        return "Evaluates a classifier on a stream by testing then training with each example in sequence and partially labelled on stream.";
    }

    private static final long serialVersionUID = 1L;

    public ClassOption learnerOption = new ClassOption("learner", 'l',
            "Learner to train.", MultiClassClassifier.class, "moa.classifiers.bayes.NaiveBayes");

    public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to learn from.", ExampleStream.class,
            "generators.RandomTreeGenerator");

    public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
            "Classification performance evaluation method.",
            LearningPerformanceEvaluator.class,
            "WindowClassificationPerformanceEvaluator");

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

    public FileOption dumpFileOption = new FileOption("dumpFile", 'd',
            "File to append intermediate csv results to.", null, "csv", true);

    public FileOption outputPredictionFileOption = new FileOption("outputPredictionFile", 'o',
            "File to append output predictions to.", null, "pred", true);

    //New for prequential method DEPRECATED
    public IntOption widthOption = new IntOption("width",
            'w', "Size of Window", 1000);

    public FloatOption alphaOption = new FloatOption("alpha",
            'a', "Fading factor or exponential smoothing factor", .01);
    //End New for prequential methods

    public IntOption percentageUnlabelledOption = new IntOption("percentageUnlabelled", 'p',
            "Percentage of amount instances unlabelled.", 0);

    public IntOption seedOption = new IntOption("seed", 'z',
            "seed of experiment", 0);
    @Override
    public Class<?> getTaskResultType() {
        return LearningCurve.class;
    }

    @Override
    protected Object doMainTask(TaskMonitor monitor, ObjectRepository repository) {
        System.out.println("Unlabelled evaluation");

        Random rnd = new Random();
        rnd.setSeed(this.seedOption.getValue());

        Learner learner = (Learner) getPreparedClassOption(this.learnerOption);
        ExampleStream stream = (ExampleStream) getPreparedClassOption(this.streamOption);
        LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(this.evaluatorOption);
        LearningCurve learningCurve = new LearningCurve("learning evaluation instances");

        learner.setModelContext(stream.getHeader());
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
            Example testInst = trainInst.copy();

            double[] prediction = learner.getVotesForInstance(testInst);
            evaluator.addResult(testInst, prediction);

            if(rnd.nextInt(100) < percentageUnlabelled){
                System.out.println("Unlabelled instance");
                System.out.println(trainInst.getData().toString());

                continue;
            }

            learner.trainOnInstance(trainInst);
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
