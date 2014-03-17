package com.rafali.common;

import java.util.List;

import com.rafali.flickruploader.AndroidDevice;
import com.rafali.flickruploader.AppInstall;

public interface AndroidRpcInterface {
	Object[] checkPremium(List<String> emails);

	void sendEmail(String recipient, String subject, String bodyHtml, String fromAddress);

	void setPremium(boolean premium, boolean purchased, List<String> emails);

	AppInstall ensureInstall(AndroidDevice androidDevice);
}
