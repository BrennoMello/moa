package moa.learners;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.AbstractClassifier;
import moa.classifiers.core.driftdetection.ChangeDetector;
import moa.core.Measurement;
import moa.options.ClassOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeDetectorEnsembleMultivariateLearner extends AbstractClassifier {

    private static final long serialVersionUID = 1L;

    public ClassOption driftDetectionMethodOption = new ClassOption("driftDetectionMethod", 'd',
            "Drift detection method to use.", ChangeDetector.class, "DDM");

    public IntOption percentageEnsembleAgreementOption = new IntOption("percentageEnsembleAgreement", 'p',
            "Percentage to use on Ensemble Agreement.", 1, 1, Integer.MAX_VALUE);

    protected HashMap<Integer, ChangeDetector> hashMapDriftDetectionMethod;

    protected boolean isEnsembleStart;

    protected int percentageEnsembleAgreement;


    @Override
    public void resetLearningImpl() {
        this.hashMapDriftDetectionMethod = new HashMap<>();
        this.isEnsembleStart = false;
        this.percentageEnsembleAgreement = this.percentageEnsembleAgreementOption.getValue();
    }

    @Override
    public void trainOnInstanceImpl(Instance inst) {

        if(!this.isEnsembleStart){
            for (int i = 0; i < inst.numAttributes(); i++) {
                ChangeDetector changeDetector = ((ChangeDetector) getPreparedClassOption(this.driftDetectionMethodOption)).copy();
                this.hashMapDriftDetectionMethod.put(i, changeDetector);
            }
            this.isEnsembleStart = true;
        } else {
            for (int i = 0; i < inst.numAttributes(); i++) {
                double attributeValue = inst.value(i);
                this.hashMapDriftDetectionMethod.get(i).input(attributeValue);
            }
        }

    }

    @Override
    public double[] getVotesForInstance(Instance inst) {
        //classVotes[0] -> is Change
        //classVotes[1] -> is in Warning Zone
        //classVotes[2] -> delay
        //classVotes[3] -> estimation


        HashMap<Double, Integer> votesChange = new HashMap<>();
        HashMap<Double, Integer> votesWarning = new HashMap<>();
        double[] output = new double[4];
        for (ChangeDetector changeDetector : this.hashMapDriftDetectionMethod.values()) {
            double [] prediction = changeDetector.getOutput();

            /*
            if(prediction[0] == 1.0) {
                System.out.println("is Change: " + prediction[0] + " Warning Zone: " + prediction[1] + " delay: " + prediction[2] + " estimation: " + prediction[3]);
            }*/

            double change = prediction[0];
            double warning = prediction[1];

            if(votesChange.containsKey(change)){
                int value = votesChange.get(change);
                value++;
                votesChange.replace(change, value);
            }else{
                votesChange.put(change, 1);
            }

            if(votesWarning.containsKey(warning)){
                int value = votesWarning.get(warning);
                value++;
                votesWarning.replace(warning, value);
            }else{
                votesWarning.put(warning, 1);
            }

            output[2] += prediction[2];
            output[3] += prediction[3];
        }

        /*
        int maxVotesChange = -1;
        double resultVotesChange = -1;
        for (Map.Entry<Double, Integer> entry : votesChange.entrySet()){
            double key = entry.getKey();
            int value = entry.getValue();
            if(maxVotesChange < value){
                resultVotesChange = key;
            }
        }
         */

        int maxVotesWarming = -1;
        double resultVotesWarming = -1;
        for (Map.Entry<Double, Integer> entry : votesWarning.entrySet()){
           double key = entry.getKey();
           int value = entry.getValue();
           if(maxVotesWarming < value){
               resultVotesWarming = key;
           }
        }


        float agreementThreshold = (percentageEnsembleAgreement*this.hashMapDriftDetectionMethod.size())/100;
        int resultVotesChange = 0;
        /*
        if(votesChange.size()>1){
            System.out.println("Votes change detected "+ votesChange.get(1.0));
        }*/
        if(votesChange.containsKey(1.0) && votesChange.get(1.0) >= agreementThreshold){
            resultVotesChange = 1;
            System.out.println("Change detected and votes "+ votesChange.get(1.0));
        }

        output[0] = resultVotesChange;
        output[1] = resultVotesWarming;
        output[2] = output[2]/this.hashMapDriftDetectionMethod.size();
        output[3] = output[3]/this.hashMapDriftDetectionMethod.size();

        return output;
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
