package moa.classifiers.core.driftdetection;

import com.github.javacliparser.IntOption;
import moa.core.ObjectRepository;
import moa.tasks.TaskMonitor;

public class NoChangeDetectorNaive extends AbstractChangeDetector {

    public IntOption numResetInstancesOption = new IntOption(
            "numResetInstances",
            'n',
            "The number of instances to detecting change.",
            30, 1, Integer.MAX_VALUE);

    private int numInstances;

    public NoChangeDetectorNaive(){
        this.resetLearning();
    }

    private static final long serialVersionUID = 1L;

    @Override
    public void input(double inputValue) {
        if (this.isChangeDetected == true || this.isInitialized == false) {
            resetLearning();
        }

        if(this.numInstances >= numResetInstancesOption.getValue()){
            this.isChangeDetected = true;
        } else {
            this.numInstances++;
        }
    }

    @Override
    public void getDescription(StringBuilder sb, int indent) {

    }

    @Override
    protected void prepareForUseImpl(TaskMonitor monitor, ObjectRepository repository) {

    }
    @Override
    public void resetLearning()
    {
        this.numInstances = 0;
        this.isInitialized = true;
        this.isChangeDetected = false;
        this.isWarningZone = false;
    }

    @Override
    public String getPurposeString(){
        return "Use this class to NOT detect changes, alert every 60 instances.";
    }


}