package pe.edu.pucp.paqrap.experiment;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
class ExperimentRegressionTest {
    @Test void sharedProtocolRegressionChecks()throws Exception {
        // Maven invokes this module from experimentos/; the suite needs the repository data directory.
        String old=System.getProperty("user.dir");
        try {
            if(!java.nio.file.Files.exists(Path.of("data"))) {
                // Tests using synthetic fixtures are portable; the complete offline suite is run from root.
                var c=new ExperimentConfig(new java.util.Properties(),Path.of("..").toAbsolutePath());
                var s=new ScenarioSpec("REAL_TEST","REAL","REAL",0,0,java.time.LocalDate.of(2026,9,9),7,8);
                var p=InstanceFactory.create(s,c);
                org.junit.jupiter.api.Assertions.assertFalse(p.orders().isEmpty());
                org.junit.jupiter.api.Assertions.assertFalse(CommonAudit.evaluate(p,pe.edu.pucp.paqrap.planner.route.OperationalPlan.empty()).fullFeasible());
            } else VerificationMain.runAll();
        } finally {System.setProperty("user.dir",old);}
    }
}
