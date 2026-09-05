package io.vykronis.policy.audit;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAuditLogTest {

    private final InMemoryAuditLog log = new InMemoryAuditLog();

    private static PolicySubject alice() {
        return PolicySubject.user("alice", "vykronis-user");
    }

    private static PolicySubject ops() {
        return PolicySubject.user("ops", "vykronis-user", "vykronis-approver");
    }

    @Test
    void appendsEntriesWithIncreasingSequenceAndLinkedHashes() {
        AuditEntry first = log.append(PolicyAction.ROLLBACK, Env.DEV, alice(), PolicyDecision.ALLOW);
        AuditEntry second = log.append(PolicyAction.ROLLBACK, Env.PROD, ops(), PolicyDecision.REQUIRE_APPROVAL);

        assertThat(first.sequence()).isEqualTo(1L);
        assertThat(second.sequence()).isEqualTo(2L);
        assertThat(second.previousHash()).isEqualTo(first.hash());
        assertThat(second.matchesPrevious(first)).isTrue();
    }

    @Test
    void fullChainVerifiesInOrder() {
        log.append(PolicyAction.ROLLBACK, Env.DEV, alice(), PolicyDecision.ALLOW);
        log.append(PolicyAction.ROLLBACK, Env.PROD, ops(), PolicyDecision.REQUIRE_APPROVAL);
        log.append(PolicyAction.ROLLBACK, Env.PROD, alice(), PolicyDecision.DENY);

        List<AuditEntry> entries = log.entries();
        assertThat(entries).hasSize(3);
        for (int i = 1; i < entries.size(); i++) {
            assertThat(entries.get(i).matchesPrevious(entries.get(i - 1)))
                    .as("entry %d chains to entry %d", i + 1, i)
                    .isTrue();
        }
    }

    @Test
    void exposedEntriesAreUnmodifiable() {
        log.append(PolicyAction.ROLLBACK, Env.DEV, alice(), PolicyDecision.ALLOW);

        assertThatThrownBy(() -> log.entries().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void tamperingWithADecisionBreaksTheHashChain() {
        log.append(PolicyAction.ROLLBACK, Env.DEV, alice(), PolicyDecision.ALLOW);
        AuditEntry recorded = log.append(PolicyAction.ROLLBACK, Env.PROD, ops(), PolicyDecision.REQUIRE_APPROVAL);

        AuditEntry tampered = new AuditEntry(
                recorded.sequence(), recorded.decidedAt(), recorded.action(), recorded.environment(),
                recorded.subject(), PolicyDecision.ALLOW, recorded.previousHash(), recorded.hash());

        assertThat(tampered.matchesPrevious(log.entries().get(0))).isFalse();
        assertThat(AuditEntry.canonical(
                tampered.sequence(), tampered.decidedAt(), tampered.action(), tampered.environment(),
                tampered.subject(), tampered.decision(), tampered.previousHash()))
                .isNotEqualTo(recorded.hash());
    }
}