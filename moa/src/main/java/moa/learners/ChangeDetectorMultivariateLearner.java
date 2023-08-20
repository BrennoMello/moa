package moa.learners;

import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.AbstractClassifier;
import moa.core.Measurement;

public class ChangeDetectorMultivariateLearner extends AbstractClassifier {


    @Override
    public double[] getVotesForInstance(Instance inst) {
        return new double[0];
    }

    @Override
    public void resetLearningImpl() {

    }

    @Override
    public void trainOnInstanceImpl(Instance inst) {

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
