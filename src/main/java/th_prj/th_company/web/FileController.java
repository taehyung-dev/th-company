package th_prj.th_company.web;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.file.FileAttachmentService;
import th_prj.th_company.member.Permission;
import th_prj.th_company.reservation.ReservationKind;
import th_prj.th_company.reservation.ReservationService;
import th_prj.th_company.schedule.ScheduleService;

/**
 * 전자결재·일정·예약 첨부파일 내려받기. 그 문서·일정을 볼 수 있는 사람만 받을 수 있다.
 */
@Controller
public class FileController {

	private final FileAttachmentService fileService;
	private final ApprovalService approvalService;
	private final ScheduleService scheduleService;
	private final ReservationService reservationService;

	public FileController(FileAttachmentService fileService, ApprovalService approvalService,
			ScheduleService scheduleService, ReservationService reservationService) {
		this.fileService = fileService;
		this.approvalService = approvalService;
		this.scheduleService = scheduleService;
		this.reservationService = reservationService;
	}

	@GetMapping("/files/{id:\\d+}")
	public ResponseEntity<Resource> download(@PathVariable Long id, Authentication authentication) {
		FileAttachmentService.Download file = fileService.download(id);
		String loginId = authentication.getName();
		// 볼 권한이 없으면 각 서비스가 AccessDeniedException 을 던진다
		switch (file.ownerType()) {
			case APPROVAL -> approvalService.view(file.ownerId(), loginId);
			case SCHEDULE -> scheduleService.get(file.ownerId(), loginId,
				SecurityUtils.can(authentication, Permission.SCHEDULE_MANAGER));
			case RESERVATION -> reservationService.get(file.ownerId(), loginId, EnumSet.noneOf(ReservationKind.class));
			case RESERVATION_SERIES -> {
				// 예약은 로그인한 사람 누구나 볼 수 있다
			}
		}
		MediaType type = file.contentType() == null
			? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.contentType());
		return ResponseEntity.ok()
			.contentType(type)
			.contentLength(file.size())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(file.name(), StandardCharsets.UTF_8).build().toString())
			.body(file.resource());
	}
}
