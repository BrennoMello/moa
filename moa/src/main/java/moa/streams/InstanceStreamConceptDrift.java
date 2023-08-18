package moa.streams;

import com.yahoo.labs.samoa.instances.Instance;
import moa.core.Example;

import java.util.List;

public interface InstanceStreamConceptDrift extends ExampleStream<Example<Instance>>{

    public List<Integer> getDriftPositions();

    public List<Integer> getDriftWidths();


}
