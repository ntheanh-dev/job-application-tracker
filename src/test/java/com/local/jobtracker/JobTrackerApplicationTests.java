package com.local.jobtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "jobtracker.data-dir=${java.io.tmpdir}/job-tracker-test")
class JobTrackerApplicationTests {
    @Test
    void contextLoads() {
    }
}
