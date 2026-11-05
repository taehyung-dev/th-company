package th_prj.th_company.reservation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ReservationForm {

	private Long id;

	/** 회의실 / 자원 (화면에서 고를 수 있는 자원 목록을 정한다) */
	@NotNull
	private ReservationKind kind = ReservationKind.ROOM;

	@NotNull(message = "자원을 선택하세요.")
	private Long resourceId;

	@NotBlank(message = "사용 목적을 입력하세요.")
	@Size(max = 100, message = "사용 목적은 100자 이하로 입력하세요.")
	private String title;

	@NotNull(message = "날짜를 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate date;

	@NotNull(message = "시작 시간을 입력하세요.")
	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime startTime;

	@NotNull(message = "종료 시간을 입력하세요.")
	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime endTime;

	/** 자원 예약의 종료일. 비우면 시작일과 같다. 회의실은 하루 안에서만 예약하므로 쓰지 않는다 */
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;

	@Size(max = 500, message = "메모는 500자 이하로 입력하세요.")
	private String memo;

	/** 회의실 새 예약의 반복 */
	private RepeatRule repeat = RepeatRule.NONE;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate repeatUntil;

	/** 반복할 때 공휴일을 건너뛴다 */
	private boolean skipHolidays = true;

	/** 반복할 때 이미 예약된 날을 건너뛴다. 아니면 하나라도 겹치면 예약하지 않는다 */
	private boolean skipConflicts;

	/** 저장할 때 지울 첨부파일 */
	private List<Long> deleteFileIds = new ArrayList<>();

	public LocalDate getEndDate() { return endDate; }
	public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
	public RepeatRule getRepeat() { return repeat; }
	public void setRepeat(RepeatRule repeat) { this.repeat = repeat; }
	public LocalDate getRepeatUntil() { return repeatUntil; }
	public void setRepeatUntil(LocalDate repeatUntil) { this.repeatUntil = repeatUntil; }
	public boolean isSkipHolidays() { return skipHolidays; }
	public void setSkipHolidays(boolean skipHolidays) { this.skipHolidays = skipHolidays; }
	public boolean isSkipConflicts() { return skipConflicts; }
	public void setSkipConflicts(boolean skipConflicts) { this.skipConflicts = skipConflicts; }
	public List<Long> getDeleteFileIds() { return deleteFileIds; }
	public void setDeleteFileIds(List<Long> deleteFileIds) { this.deleteFileIds = deleteFileIds; }
	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public ReservationKind getKind() { return kind; }
	public void setKind(ReservationKind kind) { this.kind = kind; }
	public Long getResourceId() { return resourceId; }
	public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public LocalTime getStartTime() { return startTime; }
	public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
	public LocalTime getEndTime() { return endTime; }
	public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
	public String getMemo() { return memo; }
	public void setMemo(String memo) { this.memo = memo; }
}
