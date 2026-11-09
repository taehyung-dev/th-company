package th_prj.th_company.approval;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 기안 작성/수정 폼. 결재선은 approverIds 순서대로 1단계부터. lineTypes 는 같은 순서의 방식 (LineType, 없으면 결재).
 * 제목/본문/결재선 필수 여부는 상신할 때만 검사한다 (임시저장은 비어 있어도 됨).
 */
public class DraftForm {

	private Long id;

	@NotNull
	private Long templateId;

	@Size(max = 200, message = "제목은 200자 이하로 입력하세요.")
	private String title;

	@Size(max = 100_000, message = "내용이 너무 깁니다.")
	private String content;

	private List<Long> approverIds = new ArrayList<>();

	private List<String> lineTypes = new ArrayList<>();

	/** 양식 입력 항목 값. 양식의 항목 순서대로 */
	private List<String> fieldValues = new ArrayList<>();

	/** 참조자 */
	private List<Long> ccIds = new ArrayList<>();

	/** 저장할 때 지울 첨부파일 */
	private List<Long> deleteFileIds = new ArrayList<>();

	public List<Long> getCcIds() { return ccIds; }
	public void setCcIds(List<Long> ccIds) { this.ccIds = ccIds; }
	public List<Long> getDeleteFileIds() { return deleteFileIds; }
	public void setDeleteFileIds(List<Long> deleteFileIds) { this.deleteFileIds = deleteFileIds; }
	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getTemplateId() { return templateId; }
	public void setTemplateId(Long templateId) { this.templateId = templateId; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getContent() { return content; }
	public void setContent(String content) { this.content = content; }
	public List<Long> getApproverIds() { return approverIds; }
	public void setApproverIds(List<Long> approverIds) { this.approverIds = approverIds; }
	public List<String> getLineTypes() { return lineTypes; }
	public void setLineTypes(List<String> lineTypes) { this.lineTypes = lineTypes; }
	public List<String> getFieldValues() { return fieldValues; }
	public void setFieldValues(List<String> fieldValues) { this.fieldValues = fieldValues; }
}
