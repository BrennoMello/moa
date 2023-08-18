package moa.learners;

import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.AbstractClassifier;
import moa.classifiers.Classifier;
import moa.classifiers.core.driftdetection.ChangeDetector;
import moa.core.Measurement;
import moa.options.ClassOption;

public class ChangeDetectorMultivariateLearner extends AbstractClassifier  {

    private static final long serialVersionUID = 1L;

    public ClassOption driftDetectionMethodOption = new ClassOption("driftDetectionMethod", 'd',
            "Drift detection method to use.", ChangeDetector.class, "DDM");


    protected ChangeDetector driftDetectionMethod;

    @Override
    public double[] getVotesForInstance(Instance inst) {
        return this.driftDetectionMethod.getOutput();
    }

    @Override
    public void resetLearningImpl() {
        this.driftDetectionMethod = ((ChangeDetector) getPreparedClassOption(this.driftDetectionMethodOption)).copy();
    }

    @Override
    public void trainOnInstanceImpl(Instance inst) {
        int trueClass = (int) inst.classValue();
        //System.out.println("True class train: " + trueClass);
        this.driftDetectionMethod.input(trueClass);
    }

    @Override
    protected Measurement[] getModelMeasurementsImpl() {
        return new Measurement[0];
    }

    @Override
    public void getModelDescription(StringBuilder out, int indent) {

    }

    @Override
    public boolean isRandomizable() {
        return false;
    }
}
