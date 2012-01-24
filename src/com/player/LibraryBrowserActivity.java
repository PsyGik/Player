package com.player;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashMap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.MediaStore.Audio.Albums;
import android.provider.MediaStore.Audio.Artists;
import android.provider.MediaStore.Audio.AudioColumns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.player.PlayerService.PlayerBinder;

public class LibraryBrowserActivity extends Activity {
	
	final static public int ARTISTS = 0, ALBUMS = 1, TRACKS = 2;
	private int currentLevel;
	private ListView libraryListView;
	private TextView libraryTitle;
	private int currentArtist;
	private String currentArtistName;
	private int currentAlbum;
	private String currentAlbumName;
	private ArrayAdapter<Artist> artistAdapter;
	private ArrayAdapter<Album> albumAdapter;
	private ArrayAdapter<Track> trackAdapter;
	private HashMap<Integer, Bitmap> albumArts;
	private Object mutex;
	private PlayerService playerService;
    private ServiceConnection playerServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceConnected(ComponentName arg0, IBinder service) {
			PlayerBinder playerBinder = (PlayerBinder)service;
			playerService = playerBinder.getService();
			show();
		}
		
		@Override
		public void onServiceDisconnected(ComponentName arg0) {
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.library);
		
		albumArts = new HashMap<Integer, Bitmap>();
		mutex = new Object();
		
		libraryListView = (ListView)findViewById(R.id.library_list);
		libraryTitle = (TextView)findViewById(R.id.library_title);
		libraryListView.setOnItemClickListener(new OnItemClickListener() {
			
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int pos, long arg3) {
				switch (currentLevel) {				
				case ARTISTS:
					currentLevel++;
					currentArtist = artistAdapter.getItem(pos).getId(); 
					show();
				break;
				case ALBUMS:
					currentLevel++;
					currentAlbum = albumAdapter.getItem(pos).getId(); 
					show();
				break;
				case TRACKS:
					playerService.addTrack(playerService.new Track(trackAdapter.getItem(pos).getId()));
				break;
				}
			}
		});
		artistAdapter = new ArrayAdapter<Artist>(this, R.layout.library_artist_item, 0) {
			
			@Override
			public View getView(int pos, View convertView, ViewGroup parent) {
    			View v = convertView;
    			ArtistViewHolder holder = null;    			
    			if (v == null) {
    				LayoutInflater inflater = (LayoutInflater)getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
    				v = inflater.inflate(R.layout.library_artist_item, null);
    				holder = new ArtistViewHolder();
    				holder.name = (TextView)v.findViewById(R.id.library_artist_name);
    				v.setTag(holder);
    			} else {
    				holder = (ArtistViewHolder)v.getTag();
    			}    			
    			holder.name.setText(getItem(pos).getName());
    			return v;
			}
		};
		albumAdapter = new ArrayAdapter<Album>(this, R.layout.library_album_item, 0) {
			
			@Override
			public View getView(int pos, View convertView, ViewGroup parent) {
    			View v = convertView;
    			AlbumViewHolder holder = null;    			
    			if (v == null) {
    				LayoutInflater inflater = (LayoutInflater)getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
    				v = inflater.inflate(R.layout.library_album_item, null);
    				holder = new AlbumViewHolder();
    				holder.year = (TextView)v.findViewById(R.id.library_album_year);
    				holder.name = (TextView)v.findViewById(R.id.library_album_name);
    				holder.art = (ImageView)v.findViewById(R.id.library_album_art);
    				v.setTag(holder);
    			} else {
    				holder = (AlbumViewHolder)v.getTag();
    			}    			
    			holder.year.setText(getItem(pos).getYear());
    			holder.name.setText(getItem(pos).getName());
    			new Thread(new ArtLoader(pos, getItem(pos).getArt(), holder.art)).start();
    			return v;
			}
		};
		trackAdapter = new ArrayAdapter<Track>(this, R.layout.library_track_item, 0) {
			
			@Override
			public View getView(int pos, View convertView, ViewGroup parent) {
    			View v = convertView;
    			TrackViewHolder holder = null;    			
    			if (v == null) {
    				LayoutInflater inflater = (LayoutInflater)getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
    				v = inflater.inflate(R.layout.library_track_item, null);
    				holder = new TrackViewHolder();
    				holder.name = (TextView)v.findViewById(R.id.library_track_name);
    				v.setTag(holder);
    			} else {
    				holder = (TrackViewHolder)v.getTag();
    			}    			
    			holder.name.setText(getItem(pos).getNumber()+". "+getItem(pos).getName());
    			return v;
			}
		};
	}
	
	@Override
	protected void onStart() {
		super.onStart();
    	Intent playerServiceIntent = new Intent(this, PlayerService.class);
    	getApplicationContext().bindService(playerServiceIntent, playerServiceConnection, 0);
	}
	
	@Override
	protected void onStop() {
    	getApplicationContext().unbindService(playerServiceConnection);
		super.onStop();
	}
    
    @Override
    protected void onResume() {
    	super.onResume();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    }
  
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	if ((keyCode == KeyEvent.KEYCODE_BACK) && currentLevel > 0) {
    		currentLevel--;
    		show();
    		return true;
   		}
    	return super.onKeyDown(keyCode, event);
    }
	
	private void show() {
		switch (currentLevel) {
		case ARTISTS:
			libraryTitle.setText(getResources().getString(R.string.library_artists_title));
			showArtists();
		break;
		case ALBUMS:
			Cursor artistNameCursor = managedQuery(Artists.EXTERNAL_CONTENT_URI, new String[]{Audio.Media.ARTIST}, Audio.Media._ID+" == "+currentArtist+" ", null, null);
			if (artistNameCursor.moveToFirst()) {
				currentArtistName = artistNameCursor.getString(0);
			}
			libraryTitle.setText(currentArtistName);
			albumArts.clear();
			showAlbums();
		break;
		case TRACKS:
			Cursor albumCursor = managedQuery(Albums.EXTERNAL_CONTENT_URI, new String[]{Audio.Media.ALBUM, Albums.FIRST_YEAR}, Audio.Media._ID+" == "+currentAlbum+" ", null, null);
			if (albumCursor.moveToFirst()) {
				currentAlbumName = albumCursor.getString(0);
				String albumYear = albumCursor.getString(1);
				if (albumYear != null) {
					currentAlbumName = albumYear+" - "+currentAlbumName;
				}
			}
			libraryTitle.setText(currentArtistName+" > "+currentAlbumName);
			showTracks();
		break;
		}
	}
	
	private void showArtists() {
		String[] proj = {Audio.Media._ID, Audio.Media.ARTIST};
		Cursor artistCursor = managedQuery(Artists.EXTERNAL_CONTENT_URI, proj, null, null, Audio.Media.ARTIST);		
		artistAdapter.clear();		
//		String[] columns = artistCursor.getColumnNames();
//		for (String c : columns) Toast.makeText(getApplicationContext(), c, 1000).show();
		while (artistCursor.moveToNext()) {
			artistAdapter.add(new Artist(Integer.parseInt(artistCursor.getString(0)), artistCursor.getString(1)));
		}
		libraryListView.setAdapter(artistAdapter);
	}
	
	private void showAlbums() {
		String[] proj = {Audio.Media._ID, AlbumColumns.FIRST_YEAR, Audio.Media.ALBUM, Audio.Media.ALBUM_ART};
		Cursor albumCursor = managedQuery(Albums.EXTERNAL_CONTENT_URI, proj, Audio.Media.ARTIST_ID+" == "+currentArtist+" ", null, AlbumColumns.LAST_YEAR);
		albumAdapter.clear();
		while (albumCursor.moveToNext()) {
			albumAdapter.add(new Album(Integer.parseInt(albumCursor.getString(0)), albumCursor.getString(1), albumCursor.getString(2), albumCursor.getString(3)));
		}
		libraryListView.setAdapter(albumAdapter);
	}
	
	private void showTracks() {
		String[] proj = {Audio.Media._ID, Audio.Media.TITLE, Audio.Media.TRACK};
		Cursor trackCursor = managedQuery(Audio.Media.EXTERNAL_CONTENT_URI, proj, AudioColumns.ALBUM_ID+" == "+currentAlbum+" ", null, AudioColumns.TRACK);
		trackAdapter.clear();
		int n = 0;
		while (trackCursor.moveToNext()) {
			n++;
			trackAdapter.add(new Track(Integer.parseInt(trackCursor.getString(0)), trackCursor.getString(1), n));
		}
		libraryListView.setAdapter(trackAdapter);
	}	
    
	private class ArtLoader implements Runnable {
		
		private int pos;
		private final String path;
		private ImageView artView;

		public ArtLoader(int pos, String path, ImageView artView) {
			this.pos = pos;
			this.path = path;
			this.artView = artView;
		}
		
		@Override
		public void run() {
			synchronized (mutex) {
				if (!albumArts.containsKey(pos)) {
					if (path != null) {    				
						BitmapScaler scaler = null;
						try {
							scaler = new BitmapScaler(new File(path), 80);
						} catch (IOException e) {
							e.printStackTrace();
						}
						BitmapScaler s = scaler;
						albumArts.put(pos, s.getScaled());																
					}
    			}
            	runOnUiThread(new Runnable() {
            		@Override
    		        public void run() {
            			if (albumArts.containsKey(pos)) {
            				artView.setImageBitmap(albumArts.get(pos));
            			} else {
            				artView.setImageResource(R.drawable.noart);
            			}
    		        }
               	});										    			
            	mutex.notify();
			}
		}
	}

	static private class ArtistViewHolder {
    	TextView name;
    }

    static private class AlbumViewHolder {
    	TextView year;
    	TextView name;
    	ImageView art;
    }
    
    static private class TrackViewHolder {
    	TextView name;
    }

    private class Artist {
    	private int id;
    	private String name;
    	
    	public Artist(int i, String n) {
    		id = i;
    		name = n;
    	}
    	
    	public int getId() {
    		return id;
    	}
    	
    	public String getName() {
    		return name;
    	}
    }

    private class Album {
    	private int id;
    	private String year;
    	private String name;
    	private String art;
    	
    	public Album(int i, String y, String n, String a) {
    		id = i;
    		year = y;
    		name = n;
    		art = a;
    	}
    	
    	public int getId() {
    		return id;
    	}
    	
    	public String getYear() {
    		return year;
    	}
    	
    	public String getName() {
    		return name;
    	}

    	public String getArt() {
    		return art;
    	}
    }

    private class Track {
    	private int id;
    	private String name;
    	private int number;
    	
    	public Track(int id, String name, int number) {
    		this.id = id;
    		this.name = name;
    		this.number = number;
    	}
    	
    	public int getId() {
    		return id;
    	}
    	
    	public String getName() {
    		return name;
    	}
    	
    	public int getNumber() {
    		return number;
    	}
    }
}
