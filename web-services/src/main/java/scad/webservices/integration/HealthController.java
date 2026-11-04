package scad.webservices.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import scad.webservices.sod.SodService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired
    private SodService sodService;

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("swd", "ok");
        status.put("dfs", checkPort(8333));
        status.put("sbdd", checkPort(5432));
        status.put("sodNode", sodService.getNodeId());
        status.put("sodLocked", sodService.isInCriticalSection());
        return status;
    }

    private String checkPort(int port) {
        try (java.net.Socket s = new java.net.Socket("localhost", port)) {
            return "ok";
        } catch (Exception e) {
            return "fail";
        }
    }
}