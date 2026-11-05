package th_prj.th_company.approval;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DelegationForm {

	@NotNull(message = "대결자를 선택하세요.")
	private Long delegateId;

	@NotNull(message = "시작일을 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startDate;

	@NotNull(message = "종료일을 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;

	@Size(max = 200, message = "사유는 200자 이하로 입력하세요.")
	private String reason;

	public Long getDelegateId() { return delegateId; }
	public void setDelegateId(Long delegateId) { this.delegateId = delegateId; }
	public LocalDate getStartDate() { return startDate; }
	public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
	public LocalDate getEndDate() { return endDate; }
	public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
}
