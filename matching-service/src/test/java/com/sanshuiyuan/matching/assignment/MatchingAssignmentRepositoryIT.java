package com.sanshuiyuan.matching.assignment;

import com.sanshuiyuan.matching.AbstractMysqlContainerTest;
import com.sanshuiyuan.matching.assignment.domain.MatchingAssignment;
import com.sanshuiyuan.matching.assignment.infra.MatchingAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchingAssignmentRepositoryIT extends AbstractMysqlContainerTest {

    @Autowired
    MatchingAssignmentRepository repository;

    @Test
    void save_and_query_openAssignments() {
        MatchingAssignment a = new MatchingAssignment();
        a.setRequestId(1001L);
        a.setDeviceAssetId(2001L);
        a.setOwnerUserId(3001L);
        repository.saveAndFlush(a);

        assertThat(repository.findByRequestId(1001L)).isPresent();
        assertThat(repository.countByOwnerUserIdAndReleasedAtIsNull(3001L)).isEqualTo(1L);
    }
}
