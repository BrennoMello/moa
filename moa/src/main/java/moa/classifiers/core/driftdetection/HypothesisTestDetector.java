package moa.classifiers.core.driftdetection;

import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.MultiChoiceOption;
import moa.core.ObjectRepository;
import moa.tasks.TaskMonitor;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import java.util.ArrayList;
import java.util.List;

/**
 * Drift detection method based in Hypothesis Test.
 *
 *
 * @author Brenno Mello (brenno.mello@ufba.br)
 * @version $Revision: 7 $
 */

public class HypothesisTestDetector extends AbstractChangeDetector{

    protected static final long serialVersionUID = 2L;

    public MultiChoiceOption hypothesisTestOption = new MultiChoiceOption(
            "hypothesisTest", 'h', "Hypothesis test to concept drift detect.", new String[]{
            "ks", "wrs", "tt"}, new String[]{
            "Two-sample Kolmogorov-Smirnov test",
            "Wilcoxon rank-sum test",
            "Two-sample t-test"}, 0);

    public IntOption windowSizeOption = new IntOption(
            "windowSize",
            'w',
            "The size of window to execute hypothesis test.",
            10, 1, Integer.MAX_VALUE);

    public FloatOption thresholdOption = new FloatOption(
            "threshold",
            't',
            "The threshold to execute hypothesis test.",
            0.05, 0.0, 1.0);

    private List<Double> buffer;
    private double threshold;
    private int windowSize;
    private KolmogorovSmirnovTest ksTest;
    private WilcoxonSignedRankTest wrsTest;
    private TTest tTest;

    @Override
    public void resetLearning() {
        this.buffer = new ArrayList<>();
        this.windowSize = this.windowSizeOption.getValue();
        this.threshold = this.thresholdOption.getValue();
        this.isChangeDetected = false;
        this.isWarningZone = false;
        this.ksTest = new KolmogorovSmirnovTest();
        this.wrsTest = new WilcoxonSignedRankTest();
        this.tTest = new TTest();
    }

    @Override
    public void input(double inputValue) {
        if (this.isChangeDetected == true || this.isInitialized == false) {
            resetLearning();
            this.isInitialized = true;
        }

        this.buffer.add(inputValue);
        if (this.buffer.size() > 2 * windowSize) {

            List<Double> testList = this.buffer.subList(this.buffer.size() - windowSize, this.buffer.size());
            List<Double> refList = this.buffer.subList(this.buffer.size() - windowSize * 2, this.buffer.size() - windowSize);
            double[] refVector = refList.stream().mapToDouble(Double::doubleValue).toArray();
            double[] testVector = testList.stream().mapToDouble(Double::doubleValue).toArray();
            double pvalue = 0;

            switch (this.hypothesisTestOption.getChosenIndex()) {
                case 0:
                    pvalue = this.ksTest.kolmogorovSmirnovTest(refVector, testVector);
                    break;
                case 1:
                    pvalue = this.wrsTest.wilcoxonSignedRankTest(refVector, testVector, false);
                    break;
                case 2:
                    pvalue = this.tTest.tTest(refVector, testVector);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + this.hypothesisTestOption.getChosenIndex());
            }
            //System.out.println("P-value: " + pvalue);
            if (pvalue < this.threshold) {
                //System.out.println("Drift detected: " + pvalue);
                this.isChangeDetected = true;
            }
            this.buffer = testList;
        }


    }

    @Override
    public void getDescription(StringBuilder sb, int indent) {

    }

    @Override
    protected void prepareForUseImpl(TaskMonitor monitor, ObjectRepository repository) {

    }
}
