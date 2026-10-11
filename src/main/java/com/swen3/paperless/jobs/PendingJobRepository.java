package com.swen3.paperless.jobs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PendingJobRepository extends JpaRepository<PendingJob, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PendingJob j where j.state = com.swen3.paperless.jobs.JobState.PENDING "
            + "and j.nextAttemptAt <= :now order by j.nextAttemptAt, j.id")
    List<PendingJob> lockDue(@Param("now") Instant now, Pageable page);

    @Modifying
    @Query("update PendingJob j set j.state = com.swen3.paperless.jobs.JobState.CANCELLED "
            + "where j.documentId = :id and j.kind = com.swen3.paperless.jobs.JobKind.OCR_REQUESTED "
            + "and j.state = com.swen3.paperless.jobs.JobState.PENDING")
    void cancelPendingOcr(@Param("id") UUID id);
}
