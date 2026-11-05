package th_prj.th_company.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기본 결재 양식(품의서)이 없으면 만든다. 이후 양식은 관리자 화면에서 관리할 예정.
 */
@Component
public class ApprovalTemplateInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ApprovalTemplateInitializer.class);

	/** 휴가 신청 화면이 쓰는 양식. [결재하기]에는 나오지 않도록 사용 안 함으로 둔다 */
	public static final String LEAVE_CODE = "휴가";

	private static final String PROPOSAL_CONTENT = """
		1. 목적
		 -

		2. 내용
		 -

		3. 소요 예산
		 - 금액:
		 - 집행 시기:

		4. 기대 효과
		 -

		위와 같이 품의하오니 검토 후 재가하여 주시기 바랍니다.
		""";

	private final ApprovalTemplateRepository templateRepository;

	public ApprovalTemplateInitializer(ApprovalTemplateRepository templateRepository) {
		this.templateRepository = templateRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!templateRepository.existsByCode(LEAVE_CODE)) {
			ApprovalTemplate leave = new ApprovalTemplate(LEAVE_CODE, "휴가신청서",
				"근태관리 > 휴가 신청에서 자동으로 만드는 문서입니다. 사용으로 바꾸지 마세요.", "", 900);
			leave.update(LEAVE_CODE, leave.getName(), leave.getDescription(), "", 900, false, java.util.List.of());
			templateRepository.save(leave);
		}
		if (templateRepository.existsByCode("품의")) {
			return;
		}
		templateRepository.save(new ApprovalTemplate("품의", "품의서",
			"업무 추진, 구매, 비용 집행 등에 대해 승인을 요청하는 문서", PROPOSAL_CONTENT, 10));
		log.info("기본 결재 양식(품의서)을 생성했습니다.");
	}
}
