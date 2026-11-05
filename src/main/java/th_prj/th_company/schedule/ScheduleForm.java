package th_prj.th_company.schedule;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ScheduleForm {

	private Long id;

	@NotNull
	private ScheduleType type = ScheduleType.PERSONAL;

	@NotBlank(message = "제목을 입력하세요.")
	@Size(max = 200, message = "제목은 200자 이하로 입력하세요.")
	private String title;

	@Size(max = 2000, message = "내용은 2000자 이하로 입력하세요.")
	private String description;

	private boolean allDay;

	@NotNull(message = "시작일을 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startDate;

	@DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
	private LocalTime startTime;

	@NotNull(message = "종료일을 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;

	@DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
	private LocalTime endTime;

	/** 저장할 때 지울 첨부파일 */
	private java.util.List<Long> deleteFileIds = new java.util.ArrayList<>();

	public java.util.List<Long> getDeleteFileIds() { return deleteFileIds; }
	public void setDeleteFileIds(java.util.List<Long> deleteFileIds) { this.deleteFileIds = deleteFileIds; }
	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public ScheduleType getType() { return type; }
	public void setType(ScheduleType type) { this.type = type; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public boolean isAllDay() { return allDay; }
	public void setAllDay(boolean allDay) { this.allDay = allDay; }
	public LocalDate getStartDate() { return startDate; }
	public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
	public LocalTime getStartTime() { return startTime; }
	public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
	public LocalDate getEndDate() { return endDate; }
	public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
	public LocalTime getEndTime() { return endTime; }
	public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
}
