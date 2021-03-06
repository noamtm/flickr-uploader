package com.rafali.flickruploader.tool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.androidannotations.api.BackgroundExecutor;
import org.slf4j.LoggerFactory;

import se.emilsjolander.sprinkles.CursorList;
import se.emilsjolander.sprinkles.ManyQuery;
import se.emilsjolander.sprinkles.Query;
import se.emilsjolander.sprinkles.Transaction;
import uk.co.senab.bitmapcache.BitmapLruCache;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.paypal.android.sdk.payments.PayPalConfiguration;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentActivity;
import com.paypal.android.sdk.payments.PaymentConfirmation;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.AndroidDevice;
import com.rafali.flickruploader.Config;
import com.rafali.flickruploader.FlickrUploader;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.billing.IabException;
import com.rafali.flickruploader.billing.IabHelper;
import com.rafali.flickruploader.billing.IabHelper.OnIabPurchaseFinishedListener;
import com.rafali.flickruploader.billing.IabResult;
import com.rafali.flickruploader.billing.Inventory;
import com.rafali.flickruploader.billing.Purchase;
import com.rafali.flickruploader.enums.CAN_UPLOAD;
import com.rafali.flickruploader.enums.MEDIA_TYPE;
import com.rafali.flickruploader.enums.PRIVACY;
import com.rafali.flickruploader.enums.STATUS;
import com.rafali.flickruploader.enums.VIEW_GROUP_TYPE;
import com.rafali.flickruploader.enums.VIEW_SIZE;
import com.rafali.flickruploader.model.FlickrSet;
import com.rafali.flickruploader.model.Folder;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity;
import com.rafali.flickruploader.ui.activity.FlickrWebAuthActivity_;
import com.rafali.flickruploader.ui.activity.PreferencesActivity;
import com.rafali.flickruploader2.R;

public final class Utils {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Utils.class);
	private static final float textSize = 16.0f;
	private static BitmapLruCache mCache;

	public static void confirmSignIn(final Activity context) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle("Sign into Flickr").setMessage("A Flickr account is required to upload photos.")
						.setPositiveButton("Sign in now", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								context.startActivityForResult(FlickrWebAuthActivity_.intent(context).get(), 14);
							}
						}).setNegativeButton("Later", null).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	private static void setButtonSize(AlertDialog alert) {
		alert.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
		alert.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
	}

	public static void showConfirmCancel(final Activity context, final String title, final String message, final Callback<Boolean> callback) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						callback.onResult(true);
					}
				}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						callback.onResult(false);
					}
				}).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	private static int screenWidthPx = -1;

	public static int getScreenWidthPx() {
		if (screenWidthPx < 0) {
			Point size = new Point();
			WindowManager wm = (WindowManager) FlickrUploader.getAppContext().getSystemService(Context.WINDOW_SERVICE);
			wm.getDefaultDisplay().getSize(size);
			screenWidthPx = Math.min(size.x, size.y);
		}
		return screenWidthPx;
	}

	public static int getScaledSize(float f) {
		return (int) (f * FlickrUploader.getAppContext().getResources().getDisplayMetrics().scaledDensity);
	}

	static VIEW_SIZE view_size;

	public static VIEW_SIZE getViewSize() {
		if (view_size == null) {
			try {
				view_size = VIEW_SIZE.valueOf(Utils.getStringProperty("view_size", VIEW_SIZE.medium.toString()));
			} catch (Throwable e) {
				LOG.error(ToolString.stack2string(e));
				view_size = VIEW_SIZE.medium;
			}
		}
		return view_size;
	}

	public static void setViewSize(VIEW_SIZE view_size) {
		Utils.view_size = view_size;
		Utils.setStringProperty("view_size", view_size == null ? null : view_size.toString());
	}

	static VIEW_GROUP_TYPE view_group_type;

	public static VIEW_GROUP_TYPE getViewGroupType() {
		if (view_group_type == null) {
			try {
				view_group_type = VIEW_GROUP_TYPE.valueOf(Utils.getStringProperty("view_group_type", VIEW_GROUP_TYPE.date.toString()));
			} catch (Throwable e) {
				LOG.error(ToolString.stack2string(e));
				view_group_type = VIEW_GROUP_TYPE.date;
			}
		}
		return view_group_type;
	}

	public static void setViewGroupType(VIEW_GROUP_TYPE view_group_type) {
		Utils.view_group_type = view_group_type;
		Utils.setStringProperty("view_group_type", view_group_type == null ? null : view_group_type.toString());
	}

	static Boolean show_photos;

	public static boolean getShowPhotos() {
		if (show_photos == null) {
			show_photos = getBooleanProperty("show_photos", true);
		}
		return show_photos;
	}

	public static void setShowPhotos(boolean show_photos) {
		Utils.show_photos = show_photos;
		setBooleanProperty("show_photos", show_photos);
	}

	static Boolean show_videos;

	public static boolean getShowVideos() {
		if (show_videos == null) {
			show_videos = getBooleanProperty("show_videos", true);
		}
		return show_videos;
	}

	public static void setShowVideos(Boolean show_videos) {
		Utils.show_videos = show_videos;
		setBooleanProperty("show_videos", show_videos);
	}

	static Boolean show_uploaded;

	public static void setShowUploaded(boolean show_uploaded) {
		Utils.show_uploaded = show_uploaded;
		setBooleanProperty("show_uploaded", show_uploaded);
	}

	public static boolean getShowUploaded() {
		if (show_uploaded == null) {
			show_uploaded = getBooleanProperty("show_uploaded", true);
		}
		return show_uploaded;
	}

	static Boolean show_not_uploaded;

	public static void setShowNotUploaded(boolean show_not_uploaded) {
		Utils.show_not_uploaded = show_not_uploaded;
		setBooleanProperty("show_not_uploaded", show_not_uploaded);
	}

	public static boolean getShowNotUploaded() {
		if (show_not_uploaded == null) {
			show_not_uploaded = getBooleanProperty("show_not_uploaded", true);
		}
		return show_not_uploaded;
	}

	public static void dialogPrivacy(final Activity context, final PRIVACY privacy, final Callback<PRIVACY> callback) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {

				final PRIVACY[] privacies = PRIVACY.values();
				String[] items = new String[privacies.length];
				for (int i = 0; i < PRIVACY.values().length; i++) {
					items[i] = privacies[i].getSimpleName();
				}
				int checked = -1;
				if (privacy != null) {
					checked = privacy.ordinal();
				}
				final PRIVACY[] result = new PRIVACY[1];
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle("Choose privacy").setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						result[0] = privacies[which];
					}
				}).setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (privacy != result[0])
							callback.onResult(result[0]);
					}
				}).setNegativeButton("Cancel", null).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	static final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(FlickrUploader.getAppContext());

	public static void setStringProperty(String property, String value) {
		Editor editor = sp.edit();
		if (value == null) {
			editor.remove(property);
		} else {
			editor.putString(property, value);
		}
		editor.apply();
		editor.commit();
	}

	public static String getStringProperty(String property) {
		return sp.getString(property, null);
	}

	public static String getStringProperty(String property, String defaultValue) {
		return sp.getString(property, defaultValue);
	}

	public static void clearProperty(String property) {
		Editor editor = sp.edit();
		editor.remove(property);
		editor.apply();
		editor.commit();
	}

	public static void setLongProperty(String property, Long value) {
		Editor editor = sp.edit();
		editor.putLong(property, value);
		editor.apply();
		editor.commit();
	}

	private static String email;

	public static String getEmail() {
		if (email == null) {
			email = getStringProperty(STR.email);
			if (email == null) {
				AccountManager accountManager = AccountManager.get(FlickrUploader.getAppContext());
				final Account[] accounts = accountManager.getAccountsByType("com.google");
				for (Account account : accounts) {
					if (account.name != null) {
						String name = account.name.toLowerCase(Locale.ENGLISH).trim();
						if (account.name.matches(ToolString.REGEX_EMAIL)) {
							email = name;
						}
					}
				}
				if (email == null) {
					email = getDeviceId() + "@fake.com";
				}
				setStringProperty(STR.email, email);
			}
		}
		return email;
	}

	private static String deviceId;

	public static String getDeviceId() {
		if (deviceId == null) {
			deviceId = Secure.getString(FlickrUploader.getAppContext().getContentResolver(), Secure.ANDROID_ID);
			if (deviceId == null) {
				deviceId = getStringProperty("deviceId");
				if (deviceId == null) {
					deviceId = "fake_" + UUID.randomUUID();
					setStringProperty("deviceId", deviceId);
				}
			}
		}
		return deviceId;
	}

	public static long getLongProperty(String property) {
		return sp.getLong(property, 0);
	}

	public static boolean getBooleanProperty(String property, boolean defaultValue) {
		return sp.getBoolean(property, defaultValue);
	}

	public static PRIVACY getDefaultPrivacy() {
		return PRIVACY.valueOf(sp.getString(PreferencesActivity.UPLOAD_PRIVACY, PRIVACY.PRIVATE.toString()));
	}

	public static void setBooleanProperty(String property, Boolean value) {
		Editor editor = sp.edit();
		editor.putBoolean(property, value);
		editor.apply();
		editor.commit();
	}

	public static void setImages(String key, Collection<Media> medias) {
		try {
			String serialized;
			synchronized (medias) {
				if (medias == null || medias.isEmpty()) {
					serialized = null;
				} else {
					List<Integer> ids = new ArrayList<Integer>();
					for (Media media : medias) {
						ids.add(media.getId());
					}
					serialized = Joiner.on(",").join(ids);
				}
			}
			LOG.debug("persisting images " + key + " : " + serialized);
			setStringProperty(key, serialized);
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));

		}
	}

	// public static List<Media> getImages(String key) {
	// String queueIds = getStringProperty(key);
	// if (ToolString.isNotBlank(queueIds)) {
	// String filter = Images.Media._ID + " IN (" + queueIds + ")";
	// List<Media> images = Utils.loadMedia(filter);
	// LOG.debug(key + " - queueIds : " + queueIds.split(",").length + ", images:" + images.size());
	// return images;
	// }
	// return null;
	// }

	// public static List<Media> getImages(Collection<Integer> ids) {
	// List<Media> images = null;
	// if (ids != null && !ids.isEmpty()) {
	// String filter = Images.Media._ID + " IN (" + Joiner.on(",").join(ids) + ")";
	// images = Utils.loadMedia(filter);
	// }
	// return images;
	// }

	// public static Media getImage(int id) {
	// String filter = Images.Media._ID + " IN (" + id + ")";
	// List<Media> images = Utils.loadMedia(filter);
	// if (!images.isEmpty()) {
	// return images.get(0);
	// }
	// LOG.warn("id " + id + " not found!");
	// return null;
	// }

	public static void setMapProperty(String property, Map<String, String> map) {
		Editor editor = sp.edit();
		if (map == null || map.isEmpty()) {
			editor.putString(property, null);
		} else {
			StringBuilder strb = new StringBuilder();
			Iterator<String> it = map.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next();
				String value = map.get(key);
				strb.append(key);
				strb.append("|=|");
				strb.append(value);
				if (it.hasNext())
					strb.append("|;|");
			}
			editor.putString(property, strb.toString());
		}
		editor.apply();
		editor.commit();
	}

	public static void setMapIntegerProperty(String property, Map<Integer, Integer> map) {
		Editor editor = sp.edit();
		if (map == null || map.isEmpty()) {
			editor.putString(property, null);
		} else {
			StringBuilder strb = new StringBuilder();
			Iterator<Integer> it = map.keySet().iterator();
			while (it.hasNext()) {
				Integer key = it.next();
				Integer value = map.get(key);
				strb.append(key);
				strb.append("|=|");
				strb.append(value);
				if (it.hasNext())
					strb.append("|;|");
			}
			editor.putString(property, strb.toString());
		}
		editor.apply();
		editor.commit();
	}

	public static BitmapLruCache getCache() {
		if (mCache == null) {
			BitmapLruCache.Builder builder = new BitmapLruCache.Builder(FlickrUploader.getAppContext());
			builder.setMemoryCacheEnabled(true).setMemoryCacheMaxSizeUsingHeapSize();
			mCache = builder.build();
		}
		return mCache;
	}

	public static Map<String, String> getMapProperty(String property) {
		return getMapProperty(property, false);
	}

	public static Map<String, String> getMapProperty(String property, boolean returnNull) {
		Map<String, String> map = null;
		String str = sp.getString(property, null);
		if (str != null) {
			map = new LinkedHashMap<String, String>();
			String[] entries = str.split("\\|;\\|");
			for (String entry : entries) {
				String[] split = entry.split("\\|=\\|");
				map.put(split[0], split[1]);
			}
		} else if (!returnNull) {
			map = new LinkedHashMap<String, String>();
		}
		return map;
	}

	public static Map<Integer, Integer> getMapIntegerProperty(String property) {
		Map<Integer, Integer> map = new LinkedHashMap<Integer, Integer>();
		String str = sp.getString(property, null);
		if (str != null) {
			String[] entries = str.split("\\|;\\|");
			for (String entry : entries) {
				String[] split = entry.split("\\|=\\|");
				map.put(Integer.valueOf(split[0]), Integer.valueOf(split[1]));
			}
		}
		return map;
	}

	public static String SHA1(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] sha1hash = new byte[40];
			md.update(text.getBytes("utf-8"), 0, text.length());
			sha1hash = md.digest();
			return Utils.convertToHex(sha1hash);
		} catch (Exception e) {
			LOG.warn("Error while hashing", e);
		}
		return null;
	}

	static String convertToHex(byte[] data) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('A' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	private static byte[] createChecksum(String filename) {
		try {
			InputStream fis = new FileInputStream(filename);

			byte[] buffer = new byte[1024];
			MessageDigest complete;
			complete = MessageDigest.getInstance("MD5");
			int numRead;

			do {
				numRead = fis.read(buffer);
				if (numRead > 0) {
					complete.update(buffer, 0, numRead);
				}
			} while (numRead != -1);

			fis.close();
			return complete.digest();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	// see this How-to for a faster way to convert
	// a byte array to a HEX string
	public static String getMD5Checksum(String filename) {
		byte[] b = createChecksum(filename);
		String result = "";

		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

	private static ExecutorService queue = Executors.newSingleThreadExecutor();

	public static ExecutorService getQueue() {
		if (queue == null)
			queue = Executors.newSingleThreadExecutor();
		return queue;
	}

	public static void shutdownQueueNow() {
		if (queue != null) {
			queue.shutdownNow();
			queue = null;
		}
	}

	static final String[] proj = { FileColumns._ID, FileColumns.DATA, FileColumns.MEDIA_TYPE, FileColumns.DATE_ADDED, FileColumns.SIZE, Images.Media.DATE_TAKEN };

	static long lastCached = 0;
	static List<Media> cachedMedias = new ArrayList<Media>();

	public static List<Media> loadMedia(boolean sync) {
		boolean empty = true;
		if (!sync) {
			synchronized (cachedMedias) {
				if (cachedMedias.isEmpty()) {
					long start = System.currentTimeMillis();
					cachedMedias.addAll(Query.all(Media.class).get().asList());
					lastCached = System.currentTimeMillis();
					LOG.info(cachedMedias.size() + " load from local database done in " + (System.currentTimeMillis() - start) + " ms");
					empty = cachedMedias.isEmpty();
				} else {
					empty = false;
				}
			}
		}
		if (sync || empty) {
			List<Media> syncMedia = syncMediaDatabase();
			synchronized (cachedMedias) {
				cachedMedias.clear();
				cachedMedias.addAll(syncMedia);
				lastCached = System.currentTimeMillis();
			}
			return syncMedia;
		} else {
			LOG.debug("returning " + (System.currentTimeMillis() - lastCached) + " ms old cachedMedias");
		}
		synchronized (cachedMedias) {
			return new ArrayList<Media>(cachedMedias);
		}
	}

	private static synchronized List<Media> syncMediaDatabase() {
		// Log.i("STACK", ToolString.stack2string(new Exception()));
		List<Media> syncedMedias = new ArrayList<Media>();
		long start = System.currentTimeMillis();
		Cursor cursor = null;
		Transaction t = new Transaction();
		int nbNewFiles = 0;
		try {

			ManyQuery<Media> query = Query.many(Media.class, "select * from Media order by id asc");
			CursorList<Media> cursorList = query.get();
			final int totalDatabase = cursorList.size();

			Iterator<Media> it = cursorList.iterator();
			String selection = FileColumns.MEDIA_TYPE + "=" + MEDIA_TYPE.PHOTO + " OR " + FileColumns.MEDIA_TYPE + "=" + MEDIA_TYPE.VIDEO;

			String orderBy = FileColumns._ID + " ASC";
			cursor = FlickrUploader.getAppContext().getContentResolver().query(MediaStore.Files.getContentUri("external"), proj, selection, null, orderBy);

			int idColumn = cursor.getColumnIndex(FileColumns._ID);
			int dataColumn = cursor.getColumnIndex(FileColumns.DATA);
			int mediaTypeColumn = cursor.getColumnIndex(FileColumns.MEDIA_TYPE);
			int dateAddedColumn = cursor.getColumnIndexOrThrow(FileColumns.DATE_ADDED);
			int sizeColumn = cursor.getColumnIndex(FileColumns.SIZE);
			int dateTakenColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_TAKEN);
			cursor.moveToFirst();
			final int totalMediaStore = cursor.getCount();

			final boolean shouldAutoUpload = totalDatabase > 0 && isAutoUpload() && FlickrApi.isAuthentified();

			LOG.debug("totalMediaStore = " + totalMediaStore + ", totalDatabase = " + totalDatabase);
			int progress = -1;
			Media currentMedia = null;
			int max = Math.max(totalMediaStore, totalDatabase);
			for (int i = 0; i < max; i++) {

				int newprogress = i * 100 / max;
				if (newprogress != progress) {
					progress = newprogress;
					// LOG.info("load progress : " + progress + "%");
					FlickrUploaderActivity.onLoadProgress(progress);
				}

				if (currentMedia == null && it.hasNext()) {
					currentMedia = it.next();
				}
				if (cursor.isAfterLast()) {
					if (currentMedia != null) {
						LOG.info(currentMedia + " no longer exist, we should delete it here too");
						currentMedia.deleteAsync();
						currentMedia = null;
					}
				} else {
					try {
						int mediaStoreId = cursor.getInt(idColumn);
						String data = cursor.getString(dataColumn);

						// LOG.info("i=" + i + ", mediaStoreId=" + mediaStoreId
						// + ", currentMedia=" + currentMedia);

						if (currentMedia != null && currentMedia.getId() < mediaStoreId) {
							LOG.info(currentMedia + " no longer exist, we should delete it");
							currentMedia.deleteAsync();
							currentMedia = null;
						} else if (currentMedia != null && currentMedia.getId() == mediaStoreId && currentMedia.getPath().equals(data)) {
							// LOG.info("nothing to do, already in sync");
							syncedMedias.add(currentMedia);
							currentMedia = null;
							cursor.moveToNext();
						} else {
							Media mediaToPersist;
							if (currentMedia != null && currentMedia.getId() == mediaStoreId) {
								mediaToPersist = currentMedia;
								currentMedia = null;
								mediaToPersist.setExist(true);
							} else {
								mediaToPersist = new Media(shouldAutoUpload ? STATUS.IMPORTED : STATUS.PAUSED);
								mediaToPersist.setExist(false);
							}
							syncedMedias.add(mediaToPersist);

							// LOG.info("creating new Media");
							Long date = null;
							String dateStr = null;
							try {
								dateStr = cursor.getString(dateTakenColumn);
								if (ToolString.isBlank(dateStr)) {
									dateStr = cursor.getString(dateAddedColumn);
									if (ToolString.isNotBlank(dateStr)) {
										if (dateStr.trim().length() <= 10) {
											date = Long.valueOf(dateStr) * 1000L;
										} else {
											date = Long.valueOf(dateStr);
										}
									}
								} else {
									date = Long.valueOf(dateStr);
								}
							} catch (Throwable e) {
								LOG.warn(e.getClass().getSimpleName() + " : " + dateStr);
							}
							if (date == null) {
								File file = new File(data);
								date = file.lastModified();
							}

							mediaToPersist.setId(mediaStoreId);
							mediaToPersist.setMediaType(cursor.getInt(mediaTypeColumn));
							mediaToPersist.setPath(data);
							mediaToPersist.setSize(cursor.getInt(sizeColumn));
							mediaToPersist.setTimestampCreated(date);
							mediaToPersist.save(t);

							if (totalDatabase > 0) {
								nbNewFiles++;
							}

							cursor.moveToNext();
						}
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					}
				}
			}
			t.setSuccessful(true);
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		} finally {
			t.finish();
			if (cursor != null)
				cursor.close();
		}
		if (nbNewFiles > 0) {
			FlickrUploaderActivity.onNewFiles();
		}
		LOG.info(syncedMedias.size() + " sync done in " + (System.currentTimeMillis() - start) + " ms");
		return syncedMedias;
	}

	public static String canAutoUpload() {
		if (!Utils.isAutoUpload()) {
			return "Autoupload disabled";
		}
		if (!Utils.isPremium() && !Utils.isTrial()) {
			Notifications.notifyTrialEnded();
			return "Trial has ended";
		}
		if (!FlickrApi.isAuthentified()) {
			return "Flickr not authentified yet";
		}
		return "true";
	}

	public static boolean canAutoUploadBool() {
		return "true".equals(canAutoUpload());
	}

	public static CAN_UPLOAD canUploadNow() {
		if (!FlickrApi.isAuthentified()) {
			return CAN_UPLOAD.no_flickr_login;
		}
		if (System.currentTimeMillis() < Utils.getLongProperty(STR.manuallyPaused)) {
			return CAN_UPLOAD.manually;
		}
		if (Utils.getBooleanProperty(PreferencesActivity.CHARGING_ONLY, false)) {
			if (!checkIfCharging()) {
				return CAN_UPLOAD.charging;
			}
		}

		ConnectivityManager manager = (ConnectivityManager) FlickrUploader.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = manager.getActiveNetworkInfo();

		if (activeNetwork == null || !activeNetwork.isConnected()) {
			return CAN_UPLOAD.network;
		}

		// if wifi is disabled and the user preference only allows wifi abort
		if (sp.getString(PreferencesActivity.UPLOAD_NETWORK, "").equals(STR.wifionly)) {
			switch (activeNetwork.getType()) {
			case ConnectivityManager.TYPE_MOBILE:
			case ConnectivityManager.TYPE_MOBILE_DUN:
			case ConnectivityManager.TYPE_MOBILE_HIPRI:
			case ConnectivityManager.TYPE_MOBILE_MMS:
			case ConnectivityManager.TYPE_MOBILE_SUPL:
				return CAN_UPLOAD.wifi;
			default:
				break;
			}
		}

		return CAN_UPLOAD.ok;
	}

	public static interface Callback<E> {
		public void onResult(E result);
	}

	public static <T extends Enum<T>> Map<String, T> getMapProperty(String key, Class<T> class1) {
		Map<String, String> map = getMapProperty(key);
		Map<String, T> mapE = new HashMap<String, T>();
		try {
			for (Entry<String, String> entry : map.entrySet()) {
				mapE.put(entry.getKey(), Enum.valueOf(class1, entry.getValue()));
			}
		} catch (Throwable e) {
			LOG.warn(e.getMessage(), e);
		}
		return mapE;
	}

	public static Bitmap getBitmap(Media media, VIEW_SIZE view_size) {
		Bitmap bitmap = null;
		int retry = 0;
		while (bitmap == null && retry < 3) {
			try {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = 1;
				options.inPurgeable = true;
				options.inInputShareable = true;
				if (media.getMediaType() == MEDIA_TYPE.VIDEO) {
					bitmap = Video.Thumbnails.getThumbnail(FlickrUploader.getAppContext().getContentResolver(), media.getId(), Video.Thumbnails.MINI_KIND, null);
					return bitmap;
				} else if (view_size == VIEW_SIZE.small) {
					bitmap = Images.Thumbnails.getThumbnail(FlickrUploader.getAppContext().getContentResolver(), media.getId(), Images.Thumbnails.MICRO_KIND, options);

				} else if (view_size == VIEW_SIZE.medium || view_size == VIEW_SIZE.large) {
					try {
						ExifInterface exif = new ExifInterface(media.getPath());
						if (exif.hasThumbnail()) {
							byte[] thumbnail = exif.getThumbnail();
							bitmap = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length);
						}
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					}

					if (bitmap == null) {
						bitmap = Images.Thumbnails.getThumbnail(FlickrUploader.getAppContext().getContentResolver(), media.getId(), Images.Thumbnails.MINI_KIND, options);
					}
				} else {
					// First decode with inJustDecodeBounds=true to check
					// dimensions
					final BitmapFactory.Options opts = new BitmapFactory.Options();
					opts.inJustDecodeBounds = true;
					opts.inPurgeable = true;
					opts.inInputShareable = true;
					BitmapFactory.decodeFile(media.getPath(), opts);
					// BitmapFactory.decodeFileDescriptor(file., null, opts);

					// Calculate inSampleSize
					opts.inJustDecodeBounds = false;
					opts.inSampleSize = calculateInSampleSize(opts, getScreenWidthPx(), getScreenWidthPx()) + retry;
					bitmap = BitmapFactory.decodeFile(media.getPath(), opts);
				}
			} catch (OutOfMemoryError e) {
				LOG.warn("retry : " + retry + ", " + e.getMessage(), e);
			} catch (Throwable e) {
				LOG.error(ToolString.stack2string(e));
			} finally {
				retry++;
			}
		}
		return bitmap;
	}

	public static boolean isAutoUpload() {
		return Utils.getBooleanProperty(PreferencesActivity.AUTOUPLOAD, false) || Utils.getBooleanProperty(PreferencesActivity.AUTOUPLOAD_VIDEOS, false);
	}

	public static boolean isAutoDelete() {
		return Utils.getBooleanProperty("autodeletemedia", false);
	}

	public static boolean isAutoUpload(int mediaType) {
		if (mediaType == MEDIA_TYPE.PHOTO) {
			return Utils.getBooleanProperty(PreferencesActivity.AUTOUPLOAD, false);
		} else if (mediaType == MEDIA_TYPE.VIDEO) {
			return Utils.getBooleanProperty(PreferencesActivity.AUTOUPLOAD_VIDEOS, false);
		}
		return false;
	}

	private static boolean isDefaultMediaFolder(String path) {
		if (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath().contains(path)) {
			return true;
		} else if (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath().contains(path)) {
			return true;
		} else if (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath().contains(path)) {
			return true;
		}
		return false;

	}

	static int nbFolderMonitored = -1;

	public static int getFoldersMonitoredNb() {
		if (nbFolderMonitored < 0) {
			getFolders(false);
		}
		return nbFolderMonitored;
	}

	public static synchronized Map<String, Folder> getFolders(boolean refresh) {
		CursorList<Folder> persistedFolders = Query.all(Folder.class).get();
		Map<String, Folder> persistedFoldersMap = new HashMap<String, Folder>();

		nbFolderMonitored = 0;
		for (Folder folder : persistedFolders) {
			persistedFoldersMap.put(folder.getPath(), folder);
			if (folder.isAutoUploaded()) {
				nbFolderMonitored++;
			}
		}
		final boolean emptyDatabase = persistedFoldersMap.isEmpty();

		if (emptyDatabase || refresh) {
			final Multimap<String, Media> photoFiles = LinkedHashMultimap.create();
			List<Media> medias = loadMedia(false);
			for (Media media : medias) {
				photoFiles.put(media.getFolderPath(), media);
			}

			Map<String, String> folderSetNames = Utils.getMapProperty("folderSetNames", true);

			Transaction t = new Transaction();
			try {
				for (String path : photoFiles.keySet()) {
					Folder folder = persistedFoldersMap.get(path);
					List<Media> folderMedias = new ArrayList<Media>(photoFiles.get(path));
					if (folder == null) {
						folder = new Folder(path);
						folder.setExist(false);
						if (folderSetNames != null) {
							String flickrSetTitle = folderSetNames.get(path);
							LOG.info("version migration path : " + path + ", folder : " + folder + ", flickrSetTitle : " + flickrSetTitle);
							folder.setFlickrSetTitle(flickrSetTitle);
						} else if (emptyDatabase && (folderMedias.size() > 50 || isDefaultMediaFolder(path))) {
							folder.setFlickrSetTitle(STR.instantUpload);
							nbFolderMonitored++;
						}
						persistedFoldersMap.put(path, folder);
					}
					Collections.sort(folderMedias, MEDIA_COMPARATOR);
					folder.setMedia(folderMedias.get(0));
					folder.setSize(folderMedias.size());
					folder.save(t);
				}
				t.setSuccessful(true);
			} catch (Throwable e) {
				LOG.error(ToolString.stack2string(e));
			} finally {
				t.finish();
			}
			if (folderSetNames != null) {
				Utils.setMapProperty("folderSetNames", null);
			}
		}
		return persistedFoldersMap;
	}

	public static List<String> getStringList(String key) {
		return getStringList(key, false);
	}

	public static List<String> getStringList(String key, boolean returnNull) {
		String photosSeen = sp.getString(key, null);
		if (photosSeen != null) {
			return Arrays.asList(photosSeen.split("\\|"));
		} else if (returnNull) {
			return null;
		}
		return new ArrayList<String>();
	}

	public static void setStringList(String key, Collection<String> ids) {
		setStringProperty(key, Joiner.on('|').join(ids));
	}

	/**
	 * Calculate an inSampleSize for use in a {@link BitmapFactory.Options} object when decoding bitmaps using the decode* methods from {@link BitmapFactory}. This implementation calculates the
	 * closest inSampleSize that will result in the final decoded bitmap having a width and height equal to or larger than the requested width and height. This implementation does not ensure a power
	 * of 2 is returned for inSampleSize which can be faster when decoding but results in a larger bitmap which isn't as useful for caching purposes.
	 * 
	 * @param options
	 *            An options object with out* params already populated (run through a decode* method with inJustDecodeBounds==true
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return The value to be used for inSampleSize
	 */
	static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			if (width > height) {
				inSampleSize = Math.round((float) height / (float) reqHeight);
			} else {
				inSampleSize = Math.round((float) width / (float) reqWidth);
			}

			// This offers some additional logic in case the image has a strange
			// aspect ratio. For example, a panorama may have a much larger
			// width than height. In these cases the total pixels might still
			// end up being too large to fit comfortably in memory, so we should
			// be more aggressive with sample down the image (=larger
			// inSampleSize).

			final double totalPixels = width * height;

			// Anything more than 2x the requested pixels we'll sample down
			// further.
			final double totalReqPixelsCap = reqWidth * reqHeight * 2;

			while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap && inSampleSize < 24) {
				inSampleSize++;
			}
		}
		return inSampleSize;
		// int scale = 1;
		// if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
		// scale = (int) Math.pow(2, (int) Math.round(Math.log(options.outHeight
		// / (double) Math.max(options.outHeight, options.outWidth)) /
		// Math.log(0.5)));
		// }
		// return scale;
	}

	public static <T extends Enum<T>> void setEnumMapProperty(String property, Map<String, T> mapE) {
		Map<String, String> map = new HashMap<String, String>();
		for (Entry<String, T> entry : mapE.entrySet()) {
			map.put(entry.getKey(), entry.getValue().toString());
		}
		setMapProperty(property, map);
	}

	public static String getString(int stringId, Object... objects) {
		return FlickrUploader.getAppContext().getResources().getString(stringId, objects);
	}

	public static final Comparator<Media> MEDIA_COMPARATOR = new Comparator<Media>() {
		@Override
		public int compare(Media arg0, Media arg1) {
			if (arg0.getTimestampCreated() > arg1.getTimestampCreated()) {
				return -1;
			} else if (arg0.getTimestampCreated() < arg1.getTimestampCreated()) {
				return 1;
			} else {
				return 0;
			}
		}
	};

	public static final Comparator<Media> MEDIA_COMPARATOR_UPLOAD = new Comparator<Media>() {
		@Override
		public int compare(Media arg0, Media arg1) {
			if (arg0.getTimestampUploaded() > arg1.getTimestampUploaded()) {
				return -1;
			} else if (arg0.getTimestampUploaded() < arg1.getTimestampUploaded()) {
				return 1;
			} else {
				return 0;
			}
		}
	};

	private static boolean charging = false;

	public static final void sendMail(final String subject, final String bodyHtml) {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					String admin = FlickrUploader.getAppContext().getString(R.string.admin_email);
					RPC.getRpcService().sendEmail(admin, subject, bodyHtml, admin);
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));

				}
			}
		});
	}

	public static AndroidDevice createAndroidDevice() {
		AndroidDevice androidDevice = new AndroidDevice(getDeviceId(), getAccountEmails(), Locale.getDefault().getLanguage(), Build.VERSION.SDK_INT);
		androidDevice.setAppVersion(Config.FULL_VERSION_NAME);
		androidDevice.setModelInfo(android.os.Build.MODEL + " - " + android.os.Build.VERSION.RELEASE);
		androidDevice.setCountryCode(getCountryCode());
		return androidDevice;
	}

	public static List<String> getAccountEmails() {
		List<String> emails = new ArrayList<String>();
		for (Account account : getAccountsWithEmail()) {
			emails.add(account.name);
		}
		return emails;
	}

	public static List<Account> getAccountsWithEmail() {
		List<Account> accountsEmails = new ArrayList<Account>();
		AccountManager accountManager = AccountManager.get(FlickrUploader.getAppContext());
		final Account[] accounts = accountManager.getAccountsByType("com.google");
		for (Account account : accounts) {
			if (account.name != null) {
				String name = account.name.toLowerCase(Locale.ENGLISH).trim();
				if (name.matches(ToolString.REGEX_EMAIL)) {
					accountsEmails.add(new Account(name, account.type));
				}
			}
		}
		return accountsEmails;
	}

	public static void saveAndroidDevice() {
		try {
			long lastServerDeviceSaved = Utils.getLongProperty(STR.lastServerDeviceSaved);
			if (lastServerDeviceSaved <= 0 || System.currentTimeMillis() - lastServerDeviceSaved > 5 * 24 * 3600 * 1000L) {
				RPC.getRpcService().ensureInstall(createAndroidDevice());
				Utils.setLongProperty(STR.lastServerDeviceSaved, System.currentTimeMillis());
			}
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
	}

	static String countryCode;

	public static String getCountryCode() {
		try {
			if (ToolString.isBlank(countryCode)) {
				try {
					TelephonyManager tm = (TelephonyManager) FlickrUploader.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
					countryCode = tm.getSimCountryIso();
				} catch (Throwable e) {
					LOG.warn(e.getClass().getSimpleName() + " : " + e.getMessage());
				}
			}

		} catch (Throwable e) {
		}
		return countryCode;
	}

	public static void setCharging(boolean charging) {
		Utils.charging = charging;
	}

	public static boolean checkIfCharging() {
		try {
			IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent batteryStatus = FlickrUploader.getAppContext().registerReceiver(null, ifilter);
			int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
			setCharging(isCharging);
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
		return charging;
	}

	private static boolean copyToFile(InputStream inputStream, File destFile) {
		try {
			OutputStream out = new FileOutputStream(destFile);
			try {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) >= 0) {
					out.write(buffer, 0, bytesRead);
				}
			} finally {
				out.close();
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// copy a file from srcFile to destFile, return true if succeed, return
	// false if fail
	public static boolean copyFile(File srcFile, File destFile) {
		boolean result = false;
		try {
			InputStream in = new FileInputStream(srcFile);
			try {
				result = copyToFile(in, destFile);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			result = false;
		}
		return result;
	}

	static void thankYou(final Activity activity) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(activity);
				builder.setMessage("Thank you!\n\nIf you have any suggestion to improve the app, feel free to contact me via the Feedback button in the Preferences. Also, if you have two minutes to spare, please leave a review on the Play Store to let other Flickr users know how cool my app is ;)\n\nMaxime");
				builder.setPositiveButton("Rate the app", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.rafali.flickruploader2")));
						Utils.setBooleanProperty(STR.hasRated, true);
					}
				});
				builder.setNegativeButton("Later", null);
				// Create the AlertDialog object and return it
				builder.create().show();
			}
		});
	}

	public static String getDeviceName() {
		if (Build.MODEL != null && Build.MODEL.startsWith(Build.MANUFACTURER)) {
			return Build.MODEL;
		} else {
			return Build.MANUFACTURER + " " + Build.MODEL;
		}
	}

	static boolean showingEmailActivity = false;

	public static void showEmailActivity(final Activity activity, final String subject, final String message, final boolean attachLogs) {
		if (!showingEmailActivity) {
			showingEmailActivity = true;
			BackgroundExecutor.execute(new Runnable() {

				@Override
				public void run() {
					try {
						Intent intent = new Intent(Intent.ACTION_SEND);
						intent.setType("text/email");
						intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "flickruploader@rafali.com" });
						intent.putExtra(Intent.EXTRA_SUBJECT, subject);
						intent.putExtra(Intent.EXTRA_TEXT, message);

						if (attachLogs) {
							FlickrUploader.flushLogs();
							File log = new File(FlickrUploader.getLogFilePath());
							if (log.exists()) {
								File publicDownloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
								File publicLog = new File(publicDownloadDirectory, "flickruploader_log.txt");
								Utils.copyFile(log, publicLog);
								BufferedWriter bW = null;
								try {
									bW = new BufferedWriter(new FileWriter(publicLog, true));
									bW.newLine();
									bW.write("app version : " + Config.FULL_VERSION_NAME);
									bW.newLine();
									bW.write("device id : " + getDeviceId());
									bW.newLine();
									bW.write("device name : " + getDeviceName());
									bW.newLine();
									bW.write("device accounts : " + getAccountEmails());
									bW.newLine();
									bW.write("date install : "
											+ new Date(FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).firstInstallTime));
									bW.newLine();
									bW.write("premium : " + isPremium());
									bW.newLine();
								} catch (Throwable e) {
									String stack2string = ToolString.stack2string(e);
									LOG.error(stack2string);
									bW.write(stack2string);
									bW.newLine();
								} finally {
									if (bW != null) {
										try {
											bW.flush();
											bW.close();
										} catch (Throwable e) {
											LOG.error(ToolString.stack2string(e));
										}
									}
								}
								Uri uri = Uri.fromFile(publicLog);
								intent.putExtra(Intent.EXTRA_STREAM, uri);
							} else {
								LOG.warn(log + " does not exist");
							}
						}
						final List<ResolveInfo> resInfoList = activity.getPackageManager().queryIntentActivities(intent, 0);

						ResolveInfo gmailResolveInfo = null;
						for (ResolveInfo resolveInfo : resInfoList) {
							if ("com.google.android.gm".equals(resolveInfo.activityInfo.packageName)) {
								gmailResolveInfo = resolveInfo;
								break;
							}
						}

						if (gmailResolveInfo != null) {
							intent.setClassName(gmailResolveInfo.activityInfo.packageName, gmailResolveInfo.activityInfo.name);
							activity.startActivity(intent);
						} else {
							activity.startActivity(Intent.createChooser(intent, "Send Feedback:"));
						}
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					} finally {
						showingEmailActivity = false;
					}
				}
			});
		}
	}

	public static void showCouponInfoDialog(final Activity activity) {
		setBooleanProperty(STR.couponInfo, true);

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Coupons available").setMessage("You can get the PRO version for a lower price or even for free if you help advertise it a bit.");
		builder.setNegativeButton("Later", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				LOG.debug("coupon for later then");
			}
		});
		builder.setPositiveButton("More info", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String url = "https://github.com/rafali/flickr-uploader/wiki/Coupons";
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				activity.startActivity(i);
			}
		});
		builder.create().show();
	}

	public static void showPremiumDialog(final Activity activity, final Callback<Boolean> callback) {
		View view = View.inflate(activity, R.layout.premium_dialog, null);
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		TextView description = (TextView) view.findViewById(R.id.description);
		if (Utils.isTrial()) {
			description.setText("This app can be used for free during 7 days. After " + new SimpleDateFormat("dd MMMM", Locale.US).format(new Date(Utils.trialUntil()))
					+ ", a one time payment will be required to get the PRO version and continue to use the app.");
		} else {
			description.setText("The 7-day trial of this app is now over. A one time payment is required to get the PRO version and continue to use the app.");
		}

		builder.setView(view);
		final RadioButton googleRadio = (RadioButton) view.findViewById(R.id.radio_google);

		builder.setNegativeButton("Later", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				LOG.debug("premium for later then");
			}
		}).setPositiveButton("Buy PRO Now", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (googleRadio.isChecked()) {
					startGooglePayment(activity, callback);
				} else {
					startPaypalPayment(activity, callback);
				}
			}
		});

		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);
		dialog.show();
	}

	public static void showHelpDialog(final Activity activity) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Help");
		builder.setMessage("Make sure to read the FAQ (Frequently Asked Questions) before contacting the support as your question is probably in it.");
		builder.setNegativeButton("Open FAQ", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rafali/flickr-uploader/wiki/FAQ2")));
			}
		});
		builder.setPositiveButton("Contact support", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				Utils.showEmailActivity(activity, "Feedback on Flickr Uploader " + Config.VERSION_NAME, "I have read the FAQ and I still have a question:", true);
			}

		});
		builder.create().show();
	}

	public static void showAutoUploadDialog(final Activity activity) {
		final CharSequence[] modes = { "Auto-upload new photos", "Auto-upload new videos" };
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Auto-upload");
		builder.setMultiChoiceItems(modes, new boolean[] { true, true }, null);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				ListView lw = ((AlertDialog) dialog).getListView();
				Utils.setBooleanProperty(PreferencesActivity.AUTOUPLOAD, lw.isItemChecked(0));
				Utils.setBooleanProperty(PreferencesActivity.AUTOUPLOAD_VIDEOS, lw.isItemChecked(1));
			}

		});
		builder.setNegativeButton("More options", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ListView lw = ((AlertDialog) dialog).getListView();
				Utils.setBooleanProperty(PreferencesActivity.AUTOUPLOAD, lw.isItemChecked(0));
				Utils.setBooleanProperty(PreferencesActivity.AUTOUPLOAD_VIDEOS, lw.isItemChecked(1));
				activity.startActivity(new Intent(activity, PreferencesActivity.class));
			}
		});
		builder.setCancelable(false);
		builder.create().show();
	}

	public static void showExistingSetDialog(final Activity activity, final Callback<String> callback, final Map<String, FlickrSet> cachedPhotosets) {
		final ProgressDialog dialog = ProgressDialog.show(activity, "", "Loading photosets", true);
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				final Map<String, FlickrSet> photosets = cachedPhotosets == null ? FlickrApi.getPhotoSets(true) : cachedPhotosets;
				final List<String> photosetTitles = new ArrayList<String>(photosets.keySet());
				Collections.sort(photosetTitles, String.CASE_INSENSITIVE_ORDER);
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						dialog.cancel();
						if (photosets.isEmpty()) {
							Utils.toast("No photoset found");
						} else {
							AlertDialog.Builder builder = new AlertDialog.Builder(activity);
							String[] photosetTitlesArray = photosetTitles.toArray(new String[photosetTitles.size()]);
							builder.setItems(photosetTitlesArray, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									String photoSetTitle = photosetTitles.get(which);
									callback.onResult(photoSetTitle);
								}
							});
							builder.show();
						}
					}
				});
			}
		});
	}

	private static final String CONFIG_ENVIRONMENT = PayPalConfiguration.ENVIRONMENT_PRODUCTION;
	private static final String CONFIG_CLIENT_ID = Utils.getString(R.string.paypal_client_id);
	private static final int PAYPAL_REQUEST_CODE_PAYMENT = 111000;

	private static PayPalConfiguration config = new PayPalConfiguration().environment(CONFIG_ENVIRONMENT).clientId(CONFIG_CLIENT_ID);
	private static Activity paypalActivity;
	private static Callback<Boolean> paypalCallback;

	public static void startPaypalPayment(final Activity activity, final Callback<Boolean> callback) {
		Utils.paypalActivity = activity;
		Utils.paypalCallback = callback;
		String paypal_result_confirmation = Utils.getStringProperty("paypal_result_confirmation");
		if (ToolString.isNotBlank(paypal_result_confirmation)) {
			validatePaypalPayment(paypal_result_confirmation);
		} else {
			{
				Intent intent = new Intent(activity, PayPalService.class);
				intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
				activity.startService(intent);
			}
			PayPalPayment thingToBuy = new PayPalPayment(new BigDecimal("4.99"), "USD", "Flickr Uploader License", PayPalPayment.PAYMENT_INTENT_SALE);
			Intent intent = new Intent(activity, PaymentActivity.class);
			intent.putExtra(PaymentActivity.EXTRA_PAYMENT, thingToBuy);
			activity.startActivityForResult(intent, PAYPAL_REQUEST_CODE_PAYMENT);
		}
	}

	static void validatePaypalPayment(final String paypal_result_confirmation) {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if (ToolString.isNotBlank(paypal_result_confirmation) && paypalActivity != null && paypalCallback != null) {
					toast("Processing payment");
					Boolean valid = RPC.getRpcService().confirmPaypalPayment(paypal_result_confirmation);
					if (valid != null && valid) {
						onPaymentAccepted("Paypal", paypalActivity, paypalCallback);
					} else {
						paypalCallback.onResult(false);
					}
				}
				paypalCallback = null;
				paypalActivity = null;
			}
		});
	}

	public static boolean onActivityResult(int requestCode, int resultCode, Intent data) {
		boolean consumed = false;
		if (requestCode == PAYPAL_REQUEST_CODE_PAYMENT) {
			consumed = true;
			if (resultCode == Activity.RESULT_OK) {
				PaymentConfirmation confirm = data.getParcelableExtra(PaymentActivity.EXTRA_RESULT_CONFIRMATION);
				if (confirm != null) {
					String paypal_result_confirmation = confirm.toJSONObject().toString();
					LOG.info(paypal_result_confirmation);
					Utils.setStringProperty("paypal_result_confirmation", paypal_result_confirmation);
					validatePaypalPayment(paypal_result_confirmation);
					// TODO: send 'confirm' to your server for verification
					// or consent
					// completion.
					// see
					// https://developer.paypal.com/webapps/developer/docs/integration/mobile/verify-mobile-payment/
					// for more details.
				}
			} else if (resultCode == Activity.RESULT_CANCELED) {
				LOG.info("The user canceled.");
			} else if (resultCode == PaymentActivity.RESULT_EXTRAS_INVALID) {
				LOG.warn("An invalid Payment was submitted. Please see the docs.");
			}
		} else if (IabHelper.get(false) != null && IabHelper.get(false).handleActivityResult(requestCode, resultCode, data)) {
			consumed = true;
		}
		return consumed;
	}

	private static void onPaymentAccepted(String method, final Activity activity, final Callback<Boolean> callback) {
		try {
			setPremium(true, true, true);
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					callback.onResult(true);
				}
			});
			thankYou(activity);

			long firstInstallTime = FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).firstInstallTime;
			long timeSinceInstall = System.currentTimeMillis() - firstInstallTime;

			Utils.sendMail("[FlickrUploader] PremiumSuccess " + method + " - " + ToolString.formatDuration(timeSinceInstall) + " - " + getCountryCode(), Utils.getDeviceId() + " - " + Utils.getEmail()
					+ " - " + Utils.getStringProperty(STR.userId) + " - " + Utils.getStringProperty(STR.userName));
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
	}

	public static void startGooglePayment(final Activity activity, final Callback<Boolean> callback) {
		final OnIabPurchaseFinishedListener mPurchaseFinishedListener = new OnIabPurchaseFinishedListener() {
			@Override
			public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
				try {
					LOG.debug("result : " + result + ", purchase:" + purchase);
					if (result.isFailure()) {
						if (result.getResponse() == IabHelper.IABHELPER_USER_CANCELLED) {
							showCouponInfoDialog(activity);
						} else {
						}
						callback.onResult(false);
					} else {
						onPaymentAccepted("Google", activity, callback);
					}
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				}
			}

		};
		// enable debug logging (for a production application, you should set
		// this to false).
		IabHelper.get().enableDebugLogging(Config.isDebug());

		// Start setup. This is asynchronous and the specified listener
		// will be called once setup completes.
		LOG.debug("Starting setup.");
		IabHelper.get().ensureSetup(new IabHelper.OnIabSetupFinishedListener() {
			public void onIabSetupFinished(IabResult result) {
				LOG.debug("Setup finished. : " + result);
				if (result.isSuccess()) {
					IabHelper.get().launchPurchaseFlow(activity, getPremiumSku(), 1231, mPurchaseFinishedListener, "");
				}
			}
		});
	}

	public static String getPremiumSku() {
		if (Config.isDebug()) {
			return "android.test.purchased";
		}
		if (ToolString.isNotBlank(customSku)) {
			return customSku;
		}
		return "premium.5";
	}

	public static void setPremium(final boolean notifyServer, final boolean premium, final boolean purchased) {
		LOG.debug("premium : " + premium);
		setBooleanProperty(STR.premium, premium);
		if (notifyServer) {
			BackgroundExecutor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						RPC.getRpcService().setPremium(premium, purchased, getAccountEmails());
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					}
				}
			});
		}
	}

	public static String customSku;

	public static void checkPremium(final boolean force, final Callback<Boolean> callback) {
		long lastPremiumCheck = getLongProperty(STR.lastPremiumCheck);
		LOG.debug("isPremium() : " + isPremium() + ", lastPremiumCheck : " + lastPremiumCheck);
		// check at least everyday Premium status from server
		if (!force && isPremium() && System.currentTimeMillis() - lastPremiumCheck < 24 * 60 * 60 * 1000L) {
			callback.onResult(isPremium());
		} else {
			BackgroundExecutor.execute(new Runnable() {
				@Override
				public void run() {
					boolean premium = false;
					try {
						try {
							Object[] checkPremium = RPC.getRpcService().checkPremiumStatus(createAndroidDevice());
							if (checkPremium != null && checkPremium[0] != null) {
								premium = (Boolean) checkPremium[0];
								customSku = (String) checkPremium[1];
								if (premium) {
									setPremium(false, premium, (Boolean) checkPremium[2]);
								}
								LOG.debug("server premium check: " + Arrays.toString(checkPremium));
							}
						} catch (Throwable e) {
							LOG.error(ToolString.stack2string(e));
						}
						if (!premium) {
							IabHelper.get().ensureSetup(new IabHelper.OnIabSetupFinishedListener() {
								public void onIabSetupFinished(IabResult result) {
									try {
										LOG.debug("Setup finished: " + result);
										if (result.isSuccess()) {
											Inventory queryInventory = IabHelper.get().queryInventory(true, Lists.newArrayList(Utils.getPremiumSku()));
											LOG.debug("queryInventory : " + Utils.getPremiumSku() + " : " + queryInventory.hasPurchase(Utils.getPremiumSku()));
											for (String sku : Arrays.asList("premium.5", "premium.2.5", "premium.1.25")) {
												if (queryInventory.hasPurchase(sku)) {
													LOG.debug("has purchased the app : " + sku);
													Utils.setPremium(true, true, true);
													break;
												}
											}
										}
									} catch (IabException e) {
										LOG.error(ToolString.stack2string(e));
									} finally {
										callback.onResult(isPremium());
									}
								}
							});
						} else {
							callback.onResult(isPremium());
						}
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					}
				}
			});
		}
	}

	public static boolean isPremium() {
		// return false;
		return getBooleanProperty(STR.premium, false);
	}

	public static long trialUntil() {
		try {
			long firstInstallTime = FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).firstInstallTime;
			return firstInstallTime + 7 * 24 * 3600 * 1000L;
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
		return System.currentTimeMillis() + 7 * 24 * 3600 * 1000L;
	}

	public static long nbDaysInstalled() {
		try {
			long firstInstallTime = FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).firstInstallTime;
			return (System.currentTimeMillis() - firstInstallTime) / (24 * 60 * 60 * 1000L);
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
		return 0;
	}

	public static boolean isTrial() {
		// return false;
		return trialUntil() > System.currentTimeMillis();
	}

	public static String getUploadDescription() {
		return sp.getString("upload_description", "uploaded with <a href='https://play.google.com/store/apps/details?id=com.rafali.flickruploader2'>Flickr Uploader</a> for Android");
	}

	public static String formatFileSize(long bytes) {
		return humanReadableByteCount(bytes, false);
	}

	private static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	public static long getUploadDelayMs() {
		try {
			String autoupload_delay = sp.getString("autoupload_delay", "delay0s");
			return Long.valueOf(autoupload_delay.replaceAll("[^0-9.]", "")) * 1000L;
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
		return 0;
	}

	public static long getFileSize(File file) {
		long count = 0;
		if (file.exists()) {
			if (file.isDirectory()) {
				for (File child : file.listFiles()) {
					count += getFileSize(child);
				}
			} else {
				count += file.length();
			}
			LOG.debug(file + " : " + count);
		} else {
			LOG.warn(file + " already deleted");
		}
		return count;
	}

	public static void deleteFiles(File file) {
		if (file.exists()) {
			if (file.isDirectory()) {
				for (File child : file.listFiles()) {
					deleteFiles(child);
				}
			}
			LOG.warn(file + " deleted");
			file.delete();
		} else {
			LOG.warn(file + " already deleted");
		}
	}

	public static void toast(final String message) {
		toast(message, Toast.LENGTH_LONG);
	}

	private static void toast(final String message, final int duration) {
		if (FlickrUploaderActivity.getInstance() != null) {
			FlickrUploaderActivity.getInstance().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					LOG.debug("toast : " + message);
					View view = View.inflate(FlickrUploaderActivity.getInstance(), R.layout.toast, null);

					TextView text = (TextView) view.findViewById(R.id.description);
					text.setText(message);

					Toast toast = new Toast(FlickrUploaderActivity.getInstance());
					toast.setDuration(duration);
					toast.setView(view);
					toast.show();
				}
			});
		} else {
			LOG.debug("Not toasted : " + message);
		}
	}

	public static String getRealPathFromURI(Uri uri) {
		Cursor cursor = FlickrUploader.getAppContext().getContentResolver().query(uri, null, null, null, null);
		cursor.moveToFirst();
		int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
		return cursor.getString(idx);
	}

}
