package com.chris64233.cc.assaychain.api;

import com.chris64233.cc.assaychain.api.dto.AssaySubmitRequest;
import com.chris64233.cc.assaychain.api.dto.CorrectionCreateRequest;
import com.chris64233.cc.assaychain.api.dto.CorrectionDecisionRequest;
import com.chris64233.cc.assaychain.api.dto.CustodyConfirmRequest;
import com.chris64233.cc.assaychain.api.dto.CustodyInitiateRequest;
import com.chris64233.cc.assaychain.api.dto.ReceiveRequest;
import com.chris64233.cc.assaychain.api.dto.ReviewRequest;
import com.chris64233.cc.assaychain.api.dto.SplitRequest;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.CorrectionRequest;
import com.chris64233.cc.assaychain.domain.CustodyEvent;
import com.chris64233.cc.assaychain.domain.ReviewDecision;
import com.chris64233.cc.assaychain.domain.ReviewEvent;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.domain.SplitEvent;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.BusinessRuleException;
import com.chris64233.cc.assaychain.service.CorrectionService;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.LineageService;
import com.chris64233.cc.assaychain.service.LineageView;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.ResultHistoryView;
import com.chris64233.cc.assaychain.service.ResultQueryService;
import com.chris64233.cc.assaychain.service.ResultVersionView;
import com.chris64233.cc.assaychain.service.ReviewService;
import com.chris64233.cc.assaychain.service.SampleView;
import com.chris64233.cc.assaychain.service.SplitService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api")
public class AssayChainController {

    private final ReceptionService receptionService;
    private final SplitService splitService;
    private final CustodyService custodyService;
    private final AssayService assayService;
    private final ReviewService reviewService;
    private final CorrectionService correctionService;
    private final ResultQueryService resultQueryService;
    private final LineageService lineageService;

    public AssayChainController(ReceptionService receptionService,
                                SplitService splitService,
                                CustodyService custodyService,
                                AssayService assayService,
                                ReviewService reviewService,
                                CorrectionService correctionService,
                                ResultQueryService resultQueryService,
                                LineageService lineageService) {
        this.receptionService = receptionService;
        this.splitService = splitService;
        this.custodyService = custodyService;
        this.assayService = assayService;
        this.reviewService = reviewService;
        this.correctionService = correctionService;
        this.resultQueryService = resultQueryService;
        this.lineageService = lineageService;
    }

    /** 接收原始矿样。 */
    @PostMapping("/samples")
    @ResponseStatus(HttpStatus.CREATED)
    public SampleView receive(@RequestBody ReceiveRequest request) {
        Sample sample = receptionService.receive(
                request.externalNo(),
                request.miningArea(),
                request.mass(),
                request.custodian());
        return lineageService.getSample(sample.getExternalNo());
    }

    /** 查询单个样本的质量、保管方等当前状态。 */
    @GetMapping("/samples/{externalNo}")
    public SampleView getSample(@PathVariable String externalNo) {
        return lineageService.getSample(externalNo);
    }

    /** 层级分样。 */
    @PostMapping("/splits")
    @ResponseStatus(HttpStatus.CREATED)
    public SplitEvent split(@RequestBody SplitRequest request) {
        return splitService.split(
                request.eventNo(),
                request.parentExternalNo(),
                request.declaredLossMass(),
                request.children());
    }

    /** 当前保管方发起交接。 */
    @PostMapping("/custody/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    public CustodyEvent initiateCustody(@RequestBody CustodyInitiateRequest request) {
        return custodyService.initiate(
                request.eventNo(),
                request.sampleExternalNo(),
                request.fromCustodian(),
                request.toLab());
    }

    /** 指定接收实验室确认交接。 */
    @PostMapping("/custody/confirm")
    public CustodyEvent confirmCustody(@RequestBody CustodyConfirmRequest request) {
        return custodyService.confirm(request.eventNo(), request.confirmedBy());
    }

    /** 提交检测结果（进入待复核状态）。 */
    @PostMapping("/assays")
    @ResponseStatus(HttpStatus.CREATED)
    public AssayEvent submitAssay(@RequestBody AssaySubmitRequest request) {
        return assayService.submit(
                request.eventNo(),
                request.sampleExternalNo(),
                request.itemCode(),
                request.resultValue(),
                request.unit(),
                request.submittedBy());
    }

    /** 复核检测结果：通过后结果才对外有效。 */
    @PostMapping("/results/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewEvent reviewResult(@RequestBody ReviewRequest request) {
        return reviewService.review(
                request.eventNo(),
                request.resultEventNo(),
                request.reviewedBy(),
                parseDecision(request.decision()),
                request.comment());
    }

    /** 对已生效结果发起更正申请（保存旧值/新值/原因/证据）。 */
    @PostMapping("/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public CorrectionRequest createCorrection(@RequestBody CorrectionCreateRequest request) {
        return correctionService.request(
                request.correctionNo(),
                request.resultEventNo(),
                request.newResultEventNo(),
                request.newResultValue(),
                request.newUnit(),
                request.reason(),
                request.evidence(),
                request.requestedBy());
    }

    /** 审批更正申请：批准后形成新版本，旧版本保留为历史。 */
    @PostMapping("/corrections/decide")
    public ReviewEvent decideCorrection(@RequestBody CorrectionDecisionRequest request) {
        return correctionService.decide(
                request.eventNo(),
                request.correctionNo(),
                request.reviewedBy(),
                parseDecision(request.decision()),
                request.comment());
    }

    /** 查询（样本，项目）当前对外有效结果。 */
    @GetMapping("/results/current")
    public ResultVersionView getCurrentResult(@RequestParam String sampleExternalNo,
                                              @RequestParam String itemCode) {
        return resultQueryService.getCurrent(sampleExternalNo, itemCode);
    }

    /** 联合查询：结果版本链 + 当前有效结果 + 复核记录。 */
    @GetMapping("/results/history")
    public ResultHistoryView getResultHistory(@RequestParam String sampleExternalNo,
                                              @RequestParam String itemCode) {
        return resultQueryService.getHistory(sampleExternalNo, itemCode);
    }

    /** 还原任意时点的有效结果（at 为 ISO-8601，如 2026-09-27T10:00:00Z）。 */
    @GetMapping("/results/effective-at")
    public ResultVersionView getEffectiveAt(@RequestParam String sampleExternalNo,
                                            @RequestParam String itemCode,
                                            @RequestParam String at) {
        try {
            return resultQueryService.getEffectiveAt(
                    sampleExternalNo, itemCode, Instant.parse(at));
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException("时点格式非法，应为 ISO-8601，例如 2026-09-27T10:00:00Z");
        }
    }

    /** 查询完整谱系（祖先 + 后代 + 事件时间线，含复核/更正）。 */
    @GetMapping("/samples/{externalNo}/lineage")
    public LineageView getLineage(@PathVariable String externalNo) {
        return lineageService.getLineage(externalNo);
    }

    private ReviewDecision parseDecision(String decision) {
        if (decision == null) {
            throw new BusinessRuleException("审批决定不能为空（APPROVED/REJECTED）");
        }
        return switch (decision.trim().toUpperCase()) {
            case "APPROVED" -> ReviewDecision.APPROVED;
            case "REJECTED" -> ReviewDecision.REJECTED;
            default -> throw new BusinessRuleException(
                    "审批决定只能是 APPROVED 或 REJECTED: " + decision);
        };
    }
}
