/*
 * Tomdroid
 * Tomboy on Android
 * http://www.launchpad.net/tomdroid
 * 
 * Copyright 2009, 2010, 2011 Olivier Bilodeau <olivier@bottomlesspit.org>
 * Copyright 2009, Benoit Garret <benoit.garret_launchpad@gadz.org>
 * Copyright 2010, Rodja Trappe <mail@rodja.net>
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
package org.tomdroid.sync.sd;

import android.app.Activity;
import android.os.Handler;
import android.util.TimeFormatException;
import org.tomdroid.Note;
import org.tomdroid.NoteManager;
import org.tomdroid.R;
import org.tomdroid.sync.SyncService;
import org.tomdroid.ui.Tomdroid;
import org.tomdroid.util.ErrorList;
import org.tomdroid.util.TLog;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SdCardSyncService extends SyncService {
	
	private static Pattern note_content = Pattern.compile("<note-content[^>]+>(.*)<\\/note-content>", Pattern.CASE_INSENSITIVE+Pattern.DOTALL);

	// list of notes to sync
	private ArrayList<Note> syncableNotes = new ArrayList<Note>();;

	// logging related
	private final static String TAG = "SdCardSyncService";
	
	public SdCardSyncService(Activity activity, Handler handler) {
		super(activity, handler);
	}
	
	@Override
	public int getDescriptionAsId() {
		return R.string.prefSDCard;
	}

	@Override
	public String getName() {
		return "sdcard";
	}

	@Override
	public boolean needsServer() {
		return false;
	}
	
	@Override
	public boolean needsLocation() {
		return true;
	}
	
	@Override
	public boolean needsAuth() {
		return false;
	}

	@Override
	protected void sync(boolean push) {

		setSyncProgress(0);

		// start loading local notes
		TLog.v(TAG, "Loading local notes");
		
		File path = new File(Tomdroid.NOTES_PATH);
		
		if (!path.exists())
			path.mkdir();
		
		TLog.i(TAG, "Path {0} exists: {1}", path, path.exists());
		
		// Check a second time, if not the most likely cause is the volume doesn't exist
		if(!path.exists()) {
			TLog.w(TAG, "Couldn't create {0}", path);
			sendMessage(NO_SD_CARD);
			setSyncProgress(100);
			return;
		}
		
		File[] fileList = path.listFiles(new NotesFilter());

		if(cancelled) {
			doCancel();
			return; 
		}		

		// If there are no notes, just start the sync
		if (fileList == null || fileList.length == 0) {
			TLog.i(TAG, "There are no notes in {0}", path);
			syncNotes(syncableNotes, push);
			return;
		}
		
	// get all remote notes for sync
		
		// every but the last note
		for(int i = 0; i < fileList.length-1; i++) {
			if(cancelled) {
				doCancel();
				return; 
			}
			// TODO better progress reporting from within the workers
			
			// give a filename to a thread and ask to parse it
			syncInThread(new Worker(fileList[i], false, push));
        }

		if(cancelled) {
			doCancel();
			return; 
		}
		
		// last task, warn it so it will know to start sync
		syncInThread(new Worker(fileList[fileList.length-1], true, push));
	}
	
	/**
	 * Simple filename filter that grabs files ending with .note
	 * TODO move into its own static class in a util package
	 */
	private class NotesFilter implements FilenameFilter {
		public boolean accept(File dir, String name) {
			return (name.endsWith(".note"));
		}
	}
	
	/**
	 * The worker spawns a new note, parse the file its being given by the executor.
	 */
	// TODO change type to callable to be able to throw exceptions? (if you throw make sure to display an alert only once)
	// http://java.sun.com/j2se/1.5.0/docs/api/java/util/concurrent/Callable.html
	private class Worker implements Runnable {
		
		// the note to be loaded and parsed
		private Note note = new Note();
		private File file;
		private boolean isLast;
		final char[] buffer = new char[0x1000];
		final boolean push;
		public Worker(File f, boolean isLast, boolean push) {
			file = f;
			this.isLast = isLast;
			this.push = push;
		}

		public void run() {
			
			note.setFileName(file.getAbsolutePath());
			// the note guid is not stored in the xml but in the filename
			note.setGuid(file.getName().replace(".note", ""));
			
			// Try reading the file first
			String contents = "";
			try {
				contents = readFile(file,buffer);
			} catch (IOException e) {
				e.printStackTrace();
				TLog.w(TAG, "Something went wrong trying to read the note");
				sendMessage(PARSING_FAILED, ErrorList.createError(note, e));
				onWorkDone();
				return;
			}

			try {
				// Parsing
		    	// XML 
		    	// Get a SAXParser from the SAXPArserFactory
		        SAXParserFactory spf = SAXParserFactory.newInstance();
		        SAXParser sp = spf.newSAXParser();
		
		        // Get the XMLReader of the SAXParser we created
		        XMLReader xr = sp.getXMLReader();

		        // Create a new ContentHandler, send it this note to fill and apply it to the XML-Reader
		        NoteHandler xmlHandler = new NoteHandler(note);
		        xr.setContentHandler(xmlHandler);

		        // Create the proper input source
		        StringReader sr = new StringReader(contents);
		        InputSource is = new InputSource(sr);
		        
				TLog.d(TAG, "parsing note. filename: {0}", file.getName());
				xr.parse(is);

			// TODO wrap and throw a new exception here
			} catch (Exception e) {
				e.printStackTrace();
				if(e instanceof TimeFormatException) TLog.e(TAG, "Problem parsing the note's date and time");
				sendMessage(PARSING_FAILED, ErrorList.createErrorWithContents(note, e, contents));
				onWorkDone();
				return;
			}
			
			// FIXME here we are re-reading the whole note just to grab note-content out, there is probably a better way to do this (I'm talking to you xmlpull.org!)
			Matcher m = note_content.matcher(contents);
			if (m.find()) {
				note.setXmlContent(m.group(1));
			} else {
				TLog.w(TAG, "Something went wrong trying to grab the note-content out of a note");
				sendMessage(PARSING_FAILED, ErrorList.createErrorWithContents(note, "Something went wrong trying to grab the note-content out of a note", contents));
				onWorkDone();
				return;
			}
			
			syncableNotes.add(note);
			onWorkDone();
		}
		
		private void onWorkDone(){
			if (isLast) {
				syncNotes(syncableNotes, push);
			}
		}
	}

	private String readFile(File file, char[] buffer) throws IOException {
		StringBuilder out = new StringBuilder();
		
		int read;
		Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
		
		do {
		  read = reader.read(buffer, 0, buffer.length);
		  if (read > 0) {
		    out.append(buffer, 0, read);
		  }
		}
		while (read >= 0);
		
		reader.close();
		return out.toString();
	}

	// this function either deletes or pushes, based on existence of deleted tag
	@Override
	public void pushNotes(final ArrayList<Note> notes) {
		if(notes.size() == 0)
			return;
		
		for (Note note : notes) {
			if(note.getTags().contains("system:deleted")) // deleted note
				deleteNote(note.getGuid());
			else
				pushNote(note);
		}
	}

	// this function is a shell to allow backup function to push as well but send a different message... may not be necessary any more...
	@Override
	protected void pushNote(Note note){
		TLog.v(TAG, "pushing note to sdcard");
		
		int message = doPushNote(note);

		if(message > 0)
			sendMessage(message);
		else
			sendMessage(NOTE_PUSHED);
	}
	
	// actually pushes a note to sdcard
	private int doPushNote(Note note) {

		Note rnote = new Note();
		try {
			File path = new File(Tomdroid.NOTES_PATH);
			
			if (!path.exists())
				path.mkdir();
			
			TLog.i(TAG, "Path {0} exists: {1}", path, path.exists());
			
			// Check a second time, if not the most likely cause is the volume doesn't exist
			if(!path.exists()) {
				TLog.w(TAG, "Couldn't create {0}", path);
				return NO_SD_CARD;
			}
			
			path = new File(Tomdroid.NOTES_PATH + "/"+note.getGuid() + ".note");
	
			String createDate = note.getLastChangeDate().format3339(false);
			int cursorPos = 0;
			int width = 0;
			int height = 0;
			int X = -1;
			int Y = -1;
			String tags = "";
			
			if (path.exists()) { // update existing note
	
				// Try reading the file first
				String contents = "";
				try {
					final char[] buffer = new char[0x1000];
					contents = readFile(path,buffer);
				} catch (IOException e) {
					e.printStackTrace();
					TLog.w(TAG, "Something went wrong trying to read the note");
					return PARSING_FAILED;
				}
	
				try {
					// Parsing
			    	// XML 
			    	// Get a SAXParser from the SAXPArserFactory
			        SAXParserFactory spf = SAXParserFactory.newInstance();
			        SAXParser sp = spf.newSAXParser();
			
			        // Get the XMLReader of the SAXParser we created
			        XMLReader xr = sp.getXMLReader();
	
			        // Create a new ContentHandler, send it this note to fill and apply it to the XML-Reader
			        NoteHandler xmlHandler = new NoteHandler(rnote);
			        xr.setContentHandler(xmlHandler);
	
			        // Create the proper input source
			        StringReader sr = new StringReader(contents);
			        InputSource is = new InputSource(sr);
			        
					TLog.d(TAG, "parsing note. filename: {0}", path.getName());
					xr.parse(is);
	
				// TODO wrap and throw a new exception here
				} catch (Exception e) {
					e.printStackTrace();
					if(e instanceof TimeFormatException) TLog.e(TAG, "Problem parsing the note's date and time");
					return PARSING_FAILED;
				}
	
				createDate = rnote.createDate.format3339(false);
				cursorPos = rnote.cursorPos;
				width = rnote.width;
				height = rnote.height;
				X = rnote.X;			
				Y = rnote.Y;
				
				tags = rnote.getTags();
				if(tags.length()>0) {
					String[] tagsA = tags.split(",");
					tags = "\n\t<tags>";
					for(String atag : tagsA) {
						tags += "\n\t\t<tag>"+atag+"</tag>"; 
					}
					tags += "\n\t</tags>"; 
				}
			}
	
			// TODO: create-date
			String xmlOutput = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<note version=\"0.3\" xmlns:link=\"http://beatniksoftware.com/tomboy/link\" xmlns:size=\"http://beatniksoftware.com/tomboy/size\" xmlns=\"http://beatniksoftware.com/tomboy\">\n\t<title>"+note.getTitle().replace("&", "&amp;")+"</title>\n\t<text xml:space=\"preserve\"><note-content version=\"0.1\">"+note.getXmlContent()+"</note-content></text>\n\t<last-change-date>"+note.getLastChangeDate().format3339(false)+"</last-change-date>\n\t<last-metadata-change-date>"+note.getLastChangeDate().format3339(false)+"</last-metadata-change-date>\n\t<create-date>"+createDate+"</create-date>\n\t<cursor-position>"+cursorPos+"</cursor-position>\n\t<width>"+width+"</width>\n\t<height>"+height+"</height>\n\t<x>"+X+"</x>\n\t<y>"+Y+"</y>"+tags+"\n\t<open-on-startup>False</open-on-startup>\n</note>\n";
			
			path.createNewFile();
			FileOutputStream fOut = new FileOutputStream(path);
			OutputStreamWriter myOutWriter = 
									new OutputStreamWriter(fOut);
			myOutWriter.append(xmlOutput);
			myOutWriter.close();
			fOut.close();	
	
		}
		catch (Exception e) {
			TLog.e(TAG, "push to sd card didn't work");
			return NOTE_PUSH_ERROR;
		}
		return 0;
	}

	@Override
	protected void deleteNote(String guid){
		try {
			File path = new File(Tomdroid.NOTES_PATH + "/" + guid + ".note");
			path.delete();
		}
		catch (Exception e) {
			TLog.e(TAG, "delete from sd card didn't work");
			sendMessage(NOTE_DELETE_ERROR);
			return;
		}
		sendMessage(NOTE_DELETED);

	}
	
	// pull note used for revert
	@Override
	protected void pullNote(String guid) {
		// start loading local notes
		TLog.v(TAG, "pulling remote note");
		
		File path = new File(Tomdroid.NOTES_PATH);
		
		if (!path.exists())
			path.mkdir();
		
		TLog.i(TAG, "Path {0} exists: {1}", path, path.exists());
		
		// Check a second time, if not the most likely cause is the volume doesn't exist
		if(!path.exists()) {
			TLog.w(TAG, "Couldn't create {0}", path);
			sendMessage(NO_SD_CARD);
			return;
		}
		
		path = new File(Tomdroid.NOTES_PATH + guid + ".note");

		syncInThread(new Worker(path, false, false));
		
	}
	
	// backup function accessed via preferences
	@Override
	public void backupNotes() {
		Note[] notes = NoteManager.getAllNotesAsNotes(activity, true);
		if(notes != null && notes.length > 0) 
			for(Note note : notes)
				doPushNote(note);
		sendMessage(NOTES_BACKED_UP);
	}

	@Override
	public void finishSync(boolean refresh) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteAllNotes() {
		try {
			File path = new File(Tomdroid.NOTES_PATH);
			File[] fileList = path.listFiles(new NotesFilter());
			
			for(int i = 0; i < fileList.length-1; i++) {
				fileList[i].delete();
	        }
		}
		catch (Exception e) {
			TLog.e(TAG, "delete from sd card didn't work");
			sendMessage(NOTE_DELETE_ERROR);
			return;
		}
		TLog.d(TAG, "notes deleted from SD Card");
		sendMessage(REMOTE_NOTES_DELETED);
	}
}
