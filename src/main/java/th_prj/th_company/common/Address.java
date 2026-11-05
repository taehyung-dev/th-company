package th_prj.th_company.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 주소. 우편번호/도로명/지번은 카카오 우편번호 검색 결과로 채우고, 상세주소(동·호수)만 직접 입력한다.
 * 상세주소 컬럼 이름은 쓰는 엔티티가 @AttributeOverride 로 정한다.
 */
@Embeddable
public class Address {

	/** 우편번호 (5자리 국가기초구역번호) */
	@Column(length = 10)
	private String zipCode;

	@Column(length = 200)
	private String roadAddress;

	@Column(length = 200)
	private String jibunAddress;

	@Column(length = 200)
	private String detail;

	protected Address() {
	}

	private Address(String zipCode, String roadAddress, String jibunAddress, String detail) {
		this.zipCode = zipCode;
		this.roadAddress = roadAddress;
		this.jibunAddress = jibunAddress;
		this.detail = detail;
	}

	/** 모두 비어 있으면 null */
	public static Address of(String zipCode, String roadAddress, String jibunAddress, String detail) {
		Address a = new Address(trim(zipCode), trim(roadAddress), trim(jibunAddress), trim(detail));
		return a.zipCode == null && a.roadAddress == null && a.jibunAddress == null && a.detail == null ? null : a;
	}

	private static String trim(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	public String getZipCode() { return zipCode; }
	public String getRoadAddress() { return roadAddress; }
	public String getJibunAddress() { return jibunAddress; }
	public String getDetail() { return detail; }
}
