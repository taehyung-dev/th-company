package th_prj.th_company.org;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.MemberRepository;

@Service
@Transactional(readOnly = true)
public class HrCodeService {

	private final HrCodeRepository hrCodeRepository;
	private final MemberRepository memberRepository;

	public HrCodeService(HrCodeRepository hrCodeRepository, MemberRepository memberRepository) {
		this.hrCodeRepository = hrCodeRepository;
		this.memberRepository = memberRepository;
	}

	public List<HrCode> list(HrCodeType type) {
		return hrCodeRepository.findByTypeOrderBySortOrderAscNameAsc(type);
	}

	public HrCodeForm getForm(HrCodeType type, Long id) {
		HrCode code = find(type, id);
		HrCodeForm form = new HrCodeForm();
		form.setId(code.getId());
		form.setName(code.getName());
		form.setSortOrder(code.getSortOrder());
		form.setEnabled(code.isEnabled());
		return form;
	}

	@Transactional
	public void save(HrCodeType type, HrCodeForm form) {
		String name = form.getName().trim();
		boolean duplicated = form.getId() == null
			? hrCodeRepository.existsByTypeAndName(type, name)
			: hrCodeRepository.existsByTypeAndNameAndIdNot(type, name, form.getId());
		if (duplicated) {
			throw new BusinessException("이미 등록된 " + type.getLabel() + "입니다: " + name);
		}

		if (form.getId() == null) {
			hrCodeRepository.save(new HrCode(type, name, form.getSortOrder()));
		} else {
			find(type, form.getId()).update(name, form.getSortOrder(), form.isEnabled());
		}
	}

	@Transactional
	public void delete(HrCodeType type, Long id) {
		HrCode code = find(type, id);
		if (memberRepository.existsByGradeIdOrDutyId(id, id)) {
			throw new BusinessException("사용 중인 " + type.getLabel() + "은(는) 삭제할 수 없습니다. 사용 안 함으로 변경하세요.");
		}
		hrCodeRepository.delete(code);
	}

	public HrCode find(HrCodeType type, Long id) {
		return hrCodeRepository.findById(id)
			.filter(c -> c.getType() == type)
			.orElseThrow(() -> new BusinessException(type.getLabel() + "을(를) 찾을 수 없습니다."));
	}
}
