package org.example;



import com.eka.middleware.heap.HashMap;
import com.eka.middleware.pub.util.FlowIOS;
import com.eka.middleware.service.DataPipeline;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class FlowService {
    final Map<String, Object> flow;
    final FlowIOS latest;

    public FlowService(Map<String, Object> flow, FlowIOS flowIOS) {
        this.flow = flow;
        this.latest = flowIOS;
        flow.put("latest", this.latest);
    }

    public FlowService(String description, String title, Set<String> consumers, Set<String> developers) {
        this.latest = new FlowIOS();
        this.flow = new HashMap();
        this.flow.put("latest", this.latest);
        this.flow.put("description", description);
        this.flow.put("title", title);
        this.flow.put("consumers", "");
        this.flow.put("developers", StringUtils.join(developers, ","));
        this.flow.put("enableServiceDocumentValidation", false);
       // this.latest.setLockedByUser(dataPipeline.rp.getTenant().getName());
    }

    public FlowService(String description, String title, Set<String> consumers, Set<String> developers, DataPipeline dataPipeline, Boolean enableServiceDocumentValidation) {
        this.latest = new FlowIOS();
        this.flow = new HashMap();
        this.flow.put("latest", this.latest);
        this.flow.put("description", description);
        this.flow.put("title", title);
        this.flow.put("consumers", "");
        this.flow.put("developers", StringUtils.join(developers, ","));
        this.flow.put("enableServiceDocumentValidation", enableServiceDocumentValidation);
        this.latest.setLockedByUser(dataPipeline.rp.getTenant().getName());
    }

    public Map<String, Object> getFlow() {
        return this.flow;
    }

    public FlowIOS getVersion() {
        return this.latest;
    }

    public List<Object> getInput() {
        return this.latest.getInput();
    }

    public List<Object> getOutput() {
        return this.latest.getOutput();
    }

    public List<Object> getFlowSteps() {
        return this.latest.getApi();
    }
}
