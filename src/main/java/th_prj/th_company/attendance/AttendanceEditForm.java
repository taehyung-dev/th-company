package th_prj.th_company.attendance;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 근태 정정. 출근을 비우면 그날 기록을 지운다. 퇴근이 출근보다 이르면 다음 날(새벽) 퇴근으로 본다.
 */
public class AttendanceEditForm {

	@NotNull
	private Long memberId;

	@NotNull
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate date;

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime checkIn;

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime checkOut;

	@NotBlank(message = "정정 사유를 입력하세요.")
	@Size(max = 200, message = "정정 사유는 200자 이하로 입력하세요.")
	private String note;

	public Long getMemberId() { return memberId; }
	public void setMemberId(Long memberId) { this.memberId = memberId; }
	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public LocalTime getCheckIn() { return checkIn; }
	public void setCheckIn(LocalTime checkIn) { this.checkIn = checkIn; }
	public LocalTime getCheckOut() { return checkOut; }
	public void setCheckOut(LocalTime checkOut) { this.checkOut = checkOut; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
}
