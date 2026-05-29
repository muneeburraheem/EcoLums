package com.example.ecolums;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.security.SecureRandom;

/**
 * Logic handler for Club Admins to manage challenges and recruitment links (US_03.03).
 */
public class ChallengeService {

	private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	/**
	 * AC 1: Generates a unique 6-digit alphanumeric join code.
	 */
	public String generateJoinCode() {
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
		}
		return sb.toString();
	}

	/**
	 * AC 2: Generates a QR Code Bitmap for the shareable link.
	 *
	 * @param content The URL or code to embed in the QR.
	 * @return A Bitmap image of the QR code.
	 */
	public Bitmap generateQRCode(String content) throws WriterException {
		QRCodeWriter writer = new QRCodeWriter();
		BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512);
		int width = bitMatrix.getWidth();
		int height = bitMatrix.getHeight();
		Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
			}
		}
		return bmp;
	}
}
