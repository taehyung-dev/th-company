package th_prj.th_company.holiday;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class HolidayForm {

	@NotNull(message = "날짜를 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate date;

	@NotBlank(message = "이름을 입력하세요.")
	@Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
	private String name;

	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
}
