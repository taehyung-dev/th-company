package th_prj.th_company.member;

import th_prj.th_company.common.BusinessException;

/**
 * 아이디/사번/이메일 중복. field 는 폼 필드명이라 화면에서 해당 입력칸 아래에 표시한다.
 */
public class DuplicateValueException extends BusinessException {

	private final String field;

	public DuplicateValueException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}
}
