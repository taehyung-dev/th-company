package th_prj.th_company.member;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import th_prj.th_company.common.BusinessException;

/**
 * 예약 참석자, 일정 참여자처럼 사람을 여러 명 지정하는 입력을 검사한다.
 */
@Component
public class MemberSelection {

	private final MemberRepository memberRepository;

	public MemberSelection(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	/**
	 * 지정한 사람을 찾는다. 주인(예약자, 등록자)은 뺀다.
	 *
	 * @param existing 이미 지정되어 있던 사람. 그사이 사용 중지되어도 그대로 둘 수 있다
	 * @param label    오류 문구의 이름 (참석자, 참여자)
	 */
	public List<Member> resolve(List<Long> ids, Member owner, Collection<Member> existing, int max, String label) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		Set<Long> unique = new LinkedHashSet<>(ids);
		unique.remove(owner.getId());
		if (unique.size() > max) {
			throw new BusinessException(label + "는 " + max + "명까지 지정할 수 있습니다.");
		}
		Set<Long> existingIds = existing.stream().map(Member::getId).collect(Collectors.toSet());
		Map<Long, Member> found = memberRepository.findAllById(unique).stream()
			.collect(Collectors.toMap(Member::getId, Function.identity()));
		return unique.stream().map(id -> {
			Member m = found.get(id);
			if (m == null || (!m.isEnabled() && !existingIds.contains(id))) {
				throw new BusinessException(label + "로 지정할 수 없는 사용자가 있습니다.");
			}
			return m;
		}).toList();
	}

	/** 화면 표시용 "이름 직급", 이름순 */
	public static List<String> displayNames(Collection<Member> members) {
		return members.stream()
			.sorted(Comparator.comparing(Member::getName))
			.map(m -> m.getGrade() == null ? m.getName() : m.getName() + " " + m.getGrade().getName())
			.toList();
	}
}
