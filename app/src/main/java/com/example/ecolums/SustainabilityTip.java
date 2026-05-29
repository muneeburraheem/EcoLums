package com.example.ecolums;

import com.google.firebase.Timestamp;

import java.util.List;

/**
 * Represents a sustainability tip or educational article published by an admin.
 *
 * <p>Stored in the Firestore collection {@code sustainabilityTips}. Admins
 * create, edit, and delete tips via {@link AdminDashboard}. Students read
 * them from the "Sustainability Tips" section of the app.</p>
 * <p>
 * Outstanding issues: Video embed support is stubbed — needs a video player
 * component integrated in the detail view fragment.
 */
public class SustainabilityTip {

	// -------------------------------------------------------------------------
	// Constants — categories (mirrors the folder system)
	// -------------------------------------------------------------------------

	public static final String CATEGORY_TRANSPORT = "Transport";
	public static final String CATEGORY_ENERGY = "Energy";
	public static final String CATEGORY_WASTE = "Waste";
	public static final String CATEGORY_GENERAL = "General";

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firestore document ID.
	 */
	private String tipId;

	/**
	 * Headline shown in list and detail views.
	 */
	private String title;

	/**
	 * Short snippet (≤ 200 chars) shown in list-view cards below the title.
	 */
	private String summary;

	/**
	 * Full body text of the tip, supports basic Markdown for mobile rendering.
	 */
	private String bodyContent;

	/**
	 * One of the CATEGORY_* constants, used for the folder/filter system.
	 */
	private String category;

	/**
	 * URLs of images stored in Firebase Storage to be shown in the detail view.
	 * Empty list if no images attached.
	 */
	private List<String> imageUrls;

	/**
	 * Optional URL to an embedded video (YouTube or direct link).
	 * Null if no video.
	 */
	private String videoUrl;

	/**
	 * Optional list of external links for further reading shown at the
	 * bottom of the detail view.
	 */
	private List<String> externalLinks;

	/**
	 * UID of the admin who created this tip.
	 */
	private String authorAdminId;

	/**
	 * When the tip was first published.
	 */
	private Timestamp publishedAt;

	/**
	 * When the tip was last edited. Null if never edited after publishing.
	 */
	private Timestamp lastEditedAt;

	/**
	 * Number of times this tip has been opened by students (engagement tracking).
	 */
	private int viewCount;

	/**
	 * Number of "likes" this tip has received (engagement tracking).
	 */
	private int likeCount;

	/**
	 * Whether this tip is newly published and should display a "New" badge
	 * in the list view. Automatically set to false after 7 days.
	 */
	private boolean isNew;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Required no-arg constructor for Firestore deserialization.
	 */
	public SustainabilityTip() {
	}

	/**
	 * Creates a new SustainabilityTip.
	 *
	 * @param tipId         Firestore document ID.
	 * @param title         Headline.
	 * @param summary       Short snippet for list view.
	 * @param bodyContent   Full body text.
	 * @param category      One of the CATEGORY_* constants.
	 * @param authorAdminId UID of the publishing admin.
	 * @param publishedAt   Publication timestamp.
	 */
	public SustainabilityTip(
			String tipId, String title, String summary,
			String bodyContent, String category,
			String authorAdminId, Timestamp publishedAt
	) {
		this.tipId = tipId;
		this.title = title;
		this.summary = summary;
		this.bodyContent = bodyContent;
		this.category = category;
		this.authorAdminId = authorAdminId;
		this.publishedAt = publishedAt;
		this.viewCount = 0;
		this.likeCount = 0;
		this.isNew = true;
	}

	// -------------------------------------------------------------------------
	// Getters and Setters
	// -------------------------------------------------------------------------

	public String getTipId() {
		return tipId;
	}

	public void setTipId(String v) {
		tipId = v;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String v) {
		title = v;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String v) {
		summary = v;
	}

	public String getBodyContent() {
		return bodyContent;
	}

	public void setBodyContent(String v) {
		bodyContent = v;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String v) {
		category = v;
	}

	public List<String> getImageUrls() {
		return imageUrls;
	}

	public void setImageUrls(List<String> v) {
		imageUrls = v;
	}

	public String getVideoUrl() {
		return videoUrl;
	}

	public void setVideoUrl(String v) {
		videoUrl = v;
	}

	public List<String> getExternalLinks() {
		return externalLinks;
	}

	public void setExternalLinks(List<String> v) {
		externalLinks = v;
	}

	public String getAuthorAdminId() {
		return authorAdminId;
	}

	public void setAuthorAdminId(String v) {
		authorAdminId = v;
	}

	public Timestamp getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Timestamp v) {
		publishedAt = v;
	}

	public Timestamp getLastEditedAt() {
		return lastEditedAt;
	}

	public void setLastEditedAt(Timestamp v) {
		lastEditedAt = v;
	}

	public int getViewCount() {
		return viewCount;
	}

	public void setViewCount(int v) {
		viewCount = v;
	}

	public int getLikeCount() {
		return likeCount;
	}

	public void setLikeCount(int v) {
		likeCount = v;
	}

	public boolean isNew() {
		return isNew;
	}

	public void setNew(boolean v) {
		isNew = v;
	}
}
