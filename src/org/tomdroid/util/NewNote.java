/*
 * Tomdroid
 * Tomboy on Android
 * http://www.launchpad.net/tomdroid
 * 
 * Copyright 2010 Olivier Bilodeau <olivier@bottomlesspit.org>
 * 
 * This file is part of Tomdroid.
 * 
 * Tomdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Tomdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Tomdroid.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomdroid.util;

import org.tomdroid.Note;
import org.tomdroid.R;

import java.util.UUID;
import java.util.Date;
import java.text.SimpleDateFormat;

import android.content.Context;
import android.text.format.Time;


/**
 * Creates a new note object 
 *
 */
public class NewNote {

	// Logging info
	private static final String	TAG = "NewNote";
	
	public static Note createNewNote(Context context) {
		TLog.v(TAG, "Creating new note");
		
		Note note = new Note();
		
		note.setTitle("");

		UUID newid = UUID.randomUUID();
		note.setGuid(newid.toString());
		
		Time now = new Time();
		now.setToNow();
		String time = now.format3339(false);
		TLog.i(TAG, "New note date: {0}",time);

		note.setLastChangeDate(time);
		note.setXmlContent("<note-content version=\"0.1\"></note-content>");
		
		return note;
	}

}