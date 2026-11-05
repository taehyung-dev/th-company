package th_prj.th_company.member;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import th_prj.th_company.board.FileStorage;
import th_prj.th_company.common.BusinessException;

/**
 * 프로필 사진. 올린 이미지를 가운데 기준 정사각형으로 자르고 SIZE 픽셀 JPEG 로 다시 만들어 저장한다.
 * 다시 만들기 때문에 원본에 섞인 메타데이터(촬영 위치 등)나 이미지가 아닌 내용은 남지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ProfilePhotoService {

	private static final Logger log = LoggerFactory.getLogger(ProfilePhotoService.class);

	public static final long MAX_BYTES = 5 * 1024 * 1024;
	/** 압축 해제 폭탄 방지: 이보다 큰 가로/세로는 읽지 않는다 */
	private static final int MAX_DIMENSION = 8000;
	private static final int SIZE = 320;

	private final MemberRepository memberRepository;
	private final FileStorage fileStorage;

	public ProfilePhotoService(MemberRepository memberRepository, FileStorage fileStorage) {
		this.memberRepository = memberRepository;
		this.fileStorage = fileStorage;
	}

	/**
	 * 저장 전에 미리 변환해 이미지인지 확인한다. 사용자 정보를 저장하기 전에 불러 잘못된 파일이면 아무것도 저장하지 않게 한다.
	 *
	 * @return 변환한 JPEG. 파일을 고르지 않았으면 null
	 */
	public byte[] convert(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return null;
		}
		if (file.getSize() > MAX_BYTES) {
			throw new BusinessException("프로필 사진은 5MB 이하만 올릴 수 있습니다.");
		}
		BufferedImage source;
		try (InputStream in = file.getInputStream()) {
			source = read(in);
		} catch (IOException e) {
			source = null;
		}
		if (source == null) {
			throw new BusinessException("프로필 사진은 JPG, PNG, GIF, BMP 이미지만 올릴 수 있습니다.");
		}
		return toSquareJpeg(source);
	}

	/**
	 * @param jpeg   convert 결과. null 이면 사진을 바꾸지 않는다
	 * @param remove jpeg 가 null 일 때 true 면 사진을 지운다
	 */
	@Transactional
	public void apply(Long memberId, byte[] jpeg, boolean remove) {
		if (jpeg == null && !remove) {
			return;
		}
		Member m = memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
		String old = m.changePhoto(jpeg == null ? null : fileStorage.store(jpeg));
		if (old != null) {
			deleteAfterCommit(old);
		}
	}

	/** 사용 중지된 사용자의 사진은 관리자에게만 보인다 */
	public Optional<Resource> load(Long memberId, boolean isAdmin) {
		return memberRepository.findById(memberId)
			.filter(m -> isAdmin || m.isEnabled())
			.map(Member::getPhotoPath)
			.map(fileStorage::load)
			.filter(Resource::exists);
	}

	private static BufferedImage read(InputStream in) throws IOException {
		try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
			if (iis == null) {
				return null;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext()) {
				return null;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(iis, true, true);
				if (reader.getWidth(0) > MAX_DIMENSION || reader.getHeight(0) > MAX_DIMENSION) {
					throw new BusinessException("사진의 가로·세로는 " + MAX_DIMENSION + "픽셀 이하여야 합니다.");
				}
				return reader.read(0);
			} finally {
				reader.dispose();
			}
		}
	}

	private static byte[] toSquareJpeg(BufferedImage source) {
		int side = Math.min(source.getWidth(), source.getHeight());
		int x = (source.getWidth() - side) / 2;
		int y = (source.getHeight() - side) / 2;
		int size = Math.min(SIZE, side);

		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			// 투명한 부분은 흰색으로
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, size, size);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(source, 0, 0, size, size, x, y, x + side, y + side, null);
		} finally {
			g.dispose();
		}

		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.88f);
			writer.write(null, new IIOImage(out, null, null), param);
		} catch (IOException e) {
			throw new BusinessException("프로필 사진을 처리하지 못했습니다. 다른 이미지로 다시 시도하세요.");
		} finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	/** DB 저장이 실패하면 이전 사진을 그대로 써야 하므로 커밋된 뒤에 지운다 */
	private void deleteAfterCommit(String storedName) {
		Runnable delete = () -> {
			try {
				fileStorage.delete(storedName);
			} catch (RuntimeException e) {
				log.warn("이전 프로필 사진 삭제 실패: {}", storedName, e);
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					delete.run();
				}
			});
		} else {
			delete.run();
		}
	}
}
