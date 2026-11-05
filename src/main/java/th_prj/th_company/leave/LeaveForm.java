package th_prj.th_company.leave;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LeaveForm {

	@NotNull(message = "휴가 종류를 선택하세요.")
	private LeaveType type = LeaveType.ANNUAL;

	@NotNull(message = "시작일을 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startDate;

	@NotNull(message = "종료일을 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;

	@Size(max = 500, message = "사유는 500자 이하로 입력하세요.")
	private String reason;

	/** 결재선 (위에서부터 순서대로) */
	private List<Long> approverIds = new ArrayList<>();

	public LeaveType getType() { return type; }
	public void setType(LeaveType type) { this.type = type; }
	public LocalDate getStartDate() { return startDate; }
	public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
	public LocalDate getEndDate() { return endDate; }
	public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public List<Long> getApproverIds() { return approverIds; }
	public void setApproverIds(List<Long> approverIds) { this.approverIds = approverIds; }
}
