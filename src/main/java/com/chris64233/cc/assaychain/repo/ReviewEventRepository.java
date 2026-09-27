package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.ReviewEvent;
import com.chris64233.cc.assaychain.domain.ReviewKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewEventRepository extends JpaRepository<ReviewEvent, Long> {

    Optional<ReviewEvent> findByEventNo(String eventNo);

    /** 某结果版本是否已有某类审批（每个版本只允许一次结果复核/一次更正审批）。 */
    Optional<ReviewEvent> findFirstByResultVersionIdAndKind(Long resultVersionId, ReviewKind kind);

    @Query("select r from ReviewEvent r "
            + "where r.resultVersion.id in :versionIds order by r.reviewedAt asc")
    List<ReviewEvent> findByResultVersionIdInOrderByReviewedAtAsc(
            @Param("versionIds") List<Long> versionIds);

    @Query("select r from ReviewEvent r "
            + "where r.resultVersion.sample.id in :sampleIds order by r.reviewedAt asc")
    List<ReviewEvent> findBySampleIdInOrderByReviewedAtAsc(@Param("sampleIds") List<Long> sampleIds);
}
