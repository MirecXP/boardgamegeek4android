package com.boardgamegeek.io;

import android.support.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;

import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

public abstract class PostConverter implements Converter {
	private static final String ERROR_DIV = "<div class='messagebox error'>";
	private static final String CLASS_NAME = "class com.boardgamegeek.model.PlayPostResponse";

	protected String extractErrorMessage(String content) {
		if (content.contains(ERROR_DIV)) {
			int index = content.indexOf(ERROR_DIV) + ERROR_DIV.length();
			String message = content.substring(index);
			index = message.indexOf("<");
			if (index > 0) {
				message = message.substring(0, index);
			}
			return message.trim();
		}
		return null;
	}

	@Override
	public TypedOutput toBody(Object object) {
		throw new UnsupportedOperationException();
	}

	protected void markBody(TypedInput body) throws ConversionException {
		try {
			if (body.in().markSupported()) {
				body.in().mark(10000);
			}
		} catch (IOException e) {
			throw new ConversionException(e);
		}
	}

	@NonNull
	protected String getContent(TypedInput body) throws ConversionException {
		StringBuilder sb = new StringBuilder();
		BufferedReader reader;
		try {
			body.in().reset();
			reader = new BufferedReader(new InputStreamReader(body.in(), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		} catch (IOException e) {
			throw new ConversionException(e);
		}
		return sb.toString().trim();
	}

	protected boolean typeIsExpected(Type type) {
		return CLASS_NAME.equals(type.toString());
	}
}
