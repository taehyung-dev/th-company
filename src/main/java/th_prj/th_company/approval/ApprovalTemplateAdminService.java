package th_prj.th_company.approval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.BusinessException;

/**
 * 관리자 화면의 결재 양식 관리. 이미 쓴 문서가 있는 양식은 지울 수 없고(사용 중지만), 문서번호 앞자리도 바꿀 수 없다.
 * 항목을 바꿔도 이미 쓴 문서는 저장할 때의 항목 이름·값을 그대로 보여준다.
 */
@Service
@Transactional(readOnly = true)
public class ApprovalTemplateAdminService {

	public static final int MAX_FIELDS = 20;

	private final ApprovalTemplateRepository templateRepository;
	private final ApprovalDocumentRepository documentRepository;

	public ApprovalTemplateAdminService(ApprovalTemplateRepository templateRepository,
			ApprovalDocumentRepository documentRepository) {
		this.templateRepository = templateRepository;
		this.documentRepository = documentRepository;
	}

	/** 목록 한 줄. documentCount: 이 양식으로 만든 문서 수 (보관함 포함) */
	public record Row(Long id, String code, String name, String description, int fieldCount, int sortOrder,
			boolean enabled, long documentCount) {
	}

	public List<Row> list() {
		return templateRepository.findAllByOrderBySortOrderAscNameAsc().stream()
			.map(t -> new Row(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.getFields().size(),
				t.getSortOrder(), t.isEnabled(), documentRepository.countByTemplateId(t.getId())))
			.toList();
	}

	public ApprovalTemplateForm newForm() {
		ApprovalTemplateForm form = new ApprovalTemplateForm();
		form.setSortOrder(templateRepository.findAllByOrderBySortOrderAscNameAsc().stream()
			.mapToInt(ApprovalTemplate::getSortOrder).max().orElse(0) + 10);
		return form;
	}

	public ApprovalTemplateForm getForm(Long id) {
		ApprovalTemplate t = find(id);
		ApprovalTemplateForm form = new ApprovalTemplateForm();
		form.setId(t.getId());
		form.setName(t.getName());
		form.setCode(t.getCode());
		form.setDescription(t.getDescription());
		form.setContentTemplate(t.getContentTemplate());
		form.setSortOrder(t.getSortOrder());
		form.setEnabled(t.isEnabled());
		form.setFields(new ArrayList<>(t.getFields().stream().map(ApprovalTemplateForm.FieldForm::new).toList()));
		return form;
	}

	public long documentCount(Long id) {
		return documentRepository.countByTemplateId(id);
	}

	@Transactional
	public Long save(ApprovalTemplateForm form) {
		String code = form.getCode().trim();
		String name = form.getName().trim();
		ApprovalTemplate t = form.getId() == null ? null : find(form.getId());

		boolean duplicated = t == null ? templateRepository.existsByCode(code)
			: templateRepository.existsByCodeAndIdNot(code, t.getId());
		if (duplicated) {
			throw new BusinessException("문서번호 앞자리 '" + code + "'은(는) 다른 양식이 쓰고 있습니다.");
		}
		if (t != null && !t.getCode().equals(code) && documentRepository.countByTemplateId(t.getId()) > 0) {
			throw new BusinessException("이미 작성된 문서가 있어 문서번호 앞자리는 바꿀 수 없습니다.");
		}
		if (!form.isEnabled() && (t == null || t.isEnabled()) && templateRepository.countByEnabledTrue() <= (t == null ? 0 : 1)) {
			throw new BusinessException("사용 중인 양식이 하나는 있어야 합니다.");
		}

		List<TemplateField> fields = fields(form.getFields());
		if (t == null) {
			t = templateRepository.save(new ApprovalTemplate(code, name, null, null, 0));
		}
		t.update(code, name, blankToNull(form.getDescription()), form.getContentTemplate(), form.getSortOrder(),
			form.isEnabled(), fields);
		return t.getId();
	}

	/** 이름이 빈 줄은 버린다 */
	private static List<TemplateField> fields(List<ApprovalTemplateForm.FieldForm> input) {
		List<TemplateField> result = new ArrayList<>();
		Set<String> labels = new HashSet<>();
		for (ApprovalTemplateForm.FieldForm f : input == null ? List.<ApprovalTemplateForm.FieldForm>of() : input) {
			if (f == null || f.getLabel() == null || f.getLabel().isBlank()) {
				continue;
			}
			String label = f.getLabel().trim();
			if (!labels.add(label)) {
				throw new BusinessException("입력 항목 '" + label + "'이(가) 두 번 있습니다. 항목 이름은 서로 달라야 합니다.");
			}
			FieldType type = f.getType() == null ? FieldType.TEXT : f.getType();
			TemplateField field = new TemplateField(label, type, f.isRequired(), normalizeOptions(f.getOptions()));
			if (type == FieldType.SELECT && field.optionList().isEmpty()) {
				throw new BusinessException("'" + label + "' 항목의 선택지를 쉼표로 구분해 입력하세요. (예: 연차, 반차, 병가)");
			}
			result.add(field);
		}
		if (result.size() > MAX_FIELDS) {
			throw new BusinessException("입력 항목은 " + MAX_FIELDS + "개까지 만들 수 있습니다.");
		}
		return result;
	}

	private static String normalizeOptions(String options) {
		if (options == null) {
			return null;
		}
		return String.join(", ", Arrays.stream(options.split(","))
			.map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
	}

	/** 이 양식으로 쓴 문서가 하나도 없을 때만 지울 수 있다 */
	@Transactional
	public String delete(Long id) {
		ApprovalTemplate t = find(id);
		if (documentRepository.countByTemplateId(id) > 0) {
			throw new BusinessException("이 양식으로 작성된 문서가 있어 삭제할 수 없습니다. 대신 [사용 안 함]으로 바꾸세요.");
		}
		if (t.isEnabled() && templateRepository.countByEnabledTrue() <= 1) {
			throw new BusinessException("사용 중인 양식이 하나는 있어야 합니다.");
		}
		templateRepository.delete(t);
		return t.getName();
	}

	private ApprovalTemplate find(Long id) {
		return templateRepository.findById(id)
			.orElseThrow(() -> new BusinessException("양식을 찾을 수 없습니다."));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
