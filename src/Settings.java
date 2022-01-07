import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Settings {
	public static final String DEFAULT_SETTINGS_FILE = "settings.ini";
	
	public String username = "";
	public String password = "";
	/**
	 * Experimental feature. Better use 'false' for now.
	 */
	public boolean useHeadlessMode = false; // run Chrome with --headless switch?
	public boolean restartAfterAdOfferTimeout = true; // if true, then bot will automatically restart itself if it hasn't claimed any ad offer in a time longer than defined. This is needed because ads don't appear anymore if Chrome doesn't get restarted.
	public boolean debugDetectionTimes = false; // if true, then each time a cue detection from game screenshot will be attempted, a time taken will be displayed together with a name of the cue
	public boolean hideWindowOnRestart = false; // if true, game window will be hidden upon driver (re)start
	public boolean resetTimersOnBattleEnd = true; // if true, readout timers will get reset once dungeon is cleared (or pvp or gvg or any other type of battle)
	
	public boolean doRaids = true;
	public boolean doDungeons = true;
	public boolean doTrials = true;
	public boolean doGauntlet = true;
	public boolean doPVP = true;
	public boolean doGVG = true;
	public boolean doAds = false;
	public boolean doInvasion = true;
	
	/**
	 * This is the minimum amount of shards that the bot must leave for the user. If shards get above this value, bot will play the raids in case raiding is enabled of course.
	 */
	public int minShards = 2;
	/**
	 * This is the minimum amount of tokens that the bot must leave for the user. If tokens get above this value, bot will play the trials/gauntlet in case trials/gauntlet is enabled of course.
	 */
	public byte minTokens = 5;
	/**
	 * This is the minimum amount of energy as percentage that the bot must leave for the user. If energy is higher than that, then bot will attempt to play dungeons.
	 */
	public short minEnergy = 50;
	/**
	 * This is the minimum amount of tickets that the bot must leave for the user. If tickets get above this value, bot will play the pvp in case pvp is enabled of course.
	 */
	public int minTickets = 5;
	/**
	 * This is the minimum amount of badges that the bot must leave for the user. If badges get above this value, bot will play the gvg in case gvg is enabled of course.
	 */
	public int minBadges = 5;
	public byte minBadgesGVG = 5; // option for GvG minimum badges
	
	// costs (1..5) for various events:
	public int costTickets = 2;
	public int costGVG = 2;
	public byte costTokens = 2;
	public int costBadges = 2;
	/**
	 * Max resources that a player can hold. They change based on level and perks from guild.
	 */
	public byte maxShards = 4;
	public byte maxTokens = 10;
	public short maxEnergy = 100;
	public byte maxTickets = 10;
	public byte maxBadges = 10;
	
	/**
	 * Timezone, such as 'GMT+2' or 'UTC+01:00'. The recognised prefixes are 'UTC', 'GMT' and 'UT'.
	 * The offset is the suffix and will be normalized during creation. These IDs can be normalized to a ZoneOffset using normalized()
	 */
	// currently static at this value. It should be the timezone of the game.
	public String timeZone = "GMT+0";
	
	/**
	 * The trials/gauntlet difficulty
	 */
	public int difficulty = 30;
	/**
	 * List of dungeons with percentages that we will attempt to do. Dungeon name must be in standard format, i.e. 'd2z4',
	 * followed by a space character and a difficulty level (1-3, where 1 is normal, 2 is hard, 3 is heroic), e.g. '3',
	 * and followed by a space character and percentage, e.g. '50'.
	 * Example of full string: 'z2d4 3 50'.
	 */
	public List<String> dungeons;
	/**
	 * List of raids we want to do (there are 3 raids: 1, 2 and 3) with a difficulty level and percentage.
	 * Examples:
	 * '1 3 70;2 1 30' ==> in 70% of cases it will do R1 on heroic, in 30% of cases it will do R2 normal
	 * '1 3 100' ==> in 100% of cases it will do R1 on heroic
	 */
	public List<String> raids;
	/**
	 * List of equipment that should be stripped before attempting PvP (and dressed up again after PvP is done).
	 * Allowed tokens:
	 * m = mainhand
	 * o = offhand
	 * h = head
	 * b = body
	 * n = neck
	 * r = ring
	 */
	public List<String> pvpStrip;
	
	/**
	 * If true, then bot will try to auto consume consumables as specified by the 'consumables' list.
	 */
	public boolean autoConsume = true;
	/**
	 * List of consumables that we want activate at all times.
	 */
	public List<String> consumables;
	
	/**
	 * This tells us how much time will we sleep when disconnect has been detected (which happens when a user logs in). This interval should be an hour or so, so that user can play the game in peace without being disconnected due to us reconnecting to the game.
	 */
	public int pauseOnDisconnect = 5 * MainThread.MINUTE;
	
	public Settings() {
		dungeons = new ArrayList<String>();
		setDungeons("z1d1 3 100"); // some default value
		raids = new ArrayList<String>();
		pvpStrip = new ArrayList<String>();
		consumables = new ArrayList<String>();
		setRaids("1 3 100"); // some default value
	}
	
	public void set(Settings settings) {
		this.username = settings.username;
		this.password = settings.password;
		this.useHeadlessMode = settings.useHeadlessMode;
		this.restartAfterAdOfferTimeout = settings.restartAfterAdOfferTimeout;
		this.debugDetectionTimes = settings.debugDetectionTimes;
		this.hideWindowOnRestart = settings.hideWindowOnRestart;
		this.resetTimersOnBattleEnd = settings.resetTimersOnBattleEnd;
		
		this.timeZone = settings.timeZone;
		
		this.difficulty = settings.difficulty;
		this.doRaids = settings.doRaids;
		this.doDungeons = settings.doDungeons;
		this.doTrials = settings.doTrials;
		this.doGauntlet = settings.doGauntlet;
		this.doPVP = settings.doPVP;
		this.doGVG = settings.doGVG;
		this.doInvasion = settings.doInvasion;
		this.doAds = settings.doAds;
		
		this.minShards = settings.minShards;
		this.minTokens = settings.minTokens;
		this.minEnergy = settings.minEnergy;
		this.minTickets = settings.minTickets;
		this.minBadges = settings.minBadges;
		this.minBadgesGVG = settings.minBadgesGVG;
		
		this.costTickets = settings.costTickets;
		this.costGVG = settings.costGVG;
		this.costTokens = settings.costTokens;
		this.costBadges = settings.costBadges;
		
		this.maxShards = settings.maxShards;
		this.maxTokens = settings.maxTokens;
		this.maxEnergy = settings.maxEnergy;
		this.maxTickets = settings.maxTickets;
		this.maxBadges = settings.maxBadges;
		
		this.dungeons = new ArrayList<String>(settings.dungeons);
		this.raids = new ArrayList<String>(settings.raids);
		this.pvpStrip = new ArrayList<String>(settings.pvpStrip);
		
		this.autoConsume = settings.autoConsume;
		this.consumables = new ArrayList<String>(settings.consumables);
		
		this.pauseOnDisconnect = settings.pauseOnDisconnect;
	}
	
	// a handy shortcut for some debug settings:
	public Settings setDebug() {
		doRaids = true;
		doDungeons = true;
		doGauntlet = true;
		doTrials = true;
		doPVP = true;
		doGVG = true;
		doInvasion = true;
		doAds = true;
		
		difficulty = 60;
		setDungeons("z2d1 3 50", "z2d2 3 50");
		setRaids("1 3 100");
		
		return this; // for chaining
	}
	
	/**
	 * Does nothing except collect ads
	 */
	public Settings setIdle() {
		doRaids = false;
		doDungeons = false;
		doTrials = false;
		doGauntlet = false;
		doPVP = false;
		doGVG = false;
		doInvasion = false;
		doAds = true;
		
		autoConsume = false;
		
		return this; // for chaining
	}
	
	public Settings setIdleNoAds() {
		setIdle();
		doAds = false;
		
		return this;
	}
	
	public void setDungeons(String... dungeons) {
		this.dungeons.clear();
		for (String d : dungeons) {
			String add = d.trim();
			if (add.equals(""))
				continue;
			this.dungeons.add(add);
		}
	}
	
	public void setRaids(String... raids) {
		this.raids.clear();
		for (String r : raids) {
			String add = r.trim();
			if (add.equals(""))
				continue;
			this.raids.add(add);
		}
	}
	
	public void setStrips(String... types) {
		this.pvpStrip.clear();
		for (String t : types) {
			String add = t.trim();
			if (add.equals(""))
				continue;
			this.pvpStrip.add(add);
		}
	}
	
	public void setConsumables(String... items) {
		this.consumables.clear();
		for (String i : items) {
			String add = i.trim();
			if (add.equals(""))
				continue;
			this.consumables.add(add);
		}
	}
	
	public String getDungeonsAsString() {
		String result = "";
		for (String d : dungeons)
			result += d + ";";
		if (result.length() > 0)
			result = result.substring(0, result.length() - 1); // remove last ";" character
		return result;
	}
	
	public String getRaidsAsString() {
		String result = "";
		for (String r : raids)
			result += r + ";";
		if (result.length() > 0)
			result = result.substring(0, result.length() - 1); // remove last ";" character
		return result;
	}
	
	public String getStripsAsString() {
		String result = "";
		for (String s : pvpStrip)
			result += s + " ";
		if (result.length() > 0)
			result = result.substring(0, result.length() - 1); // remove last " " character
		return result;
	}
	
	public String getConsumablesAsString() {
		String result = "";
		for (String s : consumables)
			result += s + " ";
		if (result.length() > 0)
			result = result.substring(0, result.length() - 1); // remove last " " character
		return result;
	}
	
	public void setDungeonsFromString(String s) {
		setDungeons(s.split(";"));
		// clean up (trailing spaces and remove if empty):
		for (int i = dungeons.size() - 1; i >= 0; i--) {
			dungeons.set(i, dungeons.get(i).trim());
			if (dungeons.get(i).equals(""))
				dungeons.remove(i);
		}
	}
	
	public void setRaidsFromString(String s) {
		setRaids(s.split(";"));
		// clean up (trailing spaces and remove if empty):
		for (int i = raids.size() - 1; i >= 0; i--) {
			raids.set(i, raids.get(i).trim());
			if (raids.get(i).equals(""))
				raids.remove(i);
		}
	}
	
	public void setStripsFromString(String s) {
		setStrips(s.split(" "));
		// clean up (trailing spaces and remove if empty):
		for (int i = pvpStrip.size() - 1; i >= 0; i--) {
			pvpStrip.set(i, pvpStrip.get(i).trim());
			if (pvpStrip.get(i).equals(""))
				pvpStrip.remove(i);
		}
	}
	
	public void setConsumablesFromString(String s) {
		setConsumables(s.split(" "));
		// clean up (trailing spaces and remove if empty):
		for (int i = consumables.size() - 1; i >= 0; i--) {
			consumables.set(i, consumables.get(i).trim());
			if (consumables.get(i).equals(""))
				consumables.remove(i);
		}
	}
	
	/**
	 * Loads settings from list of string arguments (which are lines of the settings.ini file, for example)
	 */
	public void load(List<String> lines) {
		Map<String, String> map = new HashMap<String, String>();
		for (String line : lines) {
			if (line.trim().equals("")) continue;
			if (line.startsWith("#")) continue; // a comment
			map.put(line.substring(0, line.indexOf(" ")), line.substring(line.indexOf(" ") + 1));
		}
		
		//time when settings load
		MainThread.timeLastSettingsCheck = Misc.getTime();
		
		// we want to use different settings depending on daily bonus
		ZoneId zoneId = ZoneId.of(timeZone);
		LocalDate localDate = LocalDate.now(zoneId);
		
		//Getting the day of week for a given date
		java.time.DayOfWeek dayOfWeek = localDate.getDayOfWeek();
		
		switch (dayOfWeek) {
			case MONDAY:
				BHBot.log("Today is a Monday. +30% Experience, +30% Capture Rate");
				setDungeonsFromString(map.getOrDefault("moDungeons", getDungeonsAsString()));
				setRaidsFromString(map.getOrDefault("moRaids", getRaidsAsString()));
				difficulty = Integer.parseInt(map.getOrDefault("moDifficulty", "" + difficulty));
				break;
			case TUESDAY:
				BHBot.log("Today is a Tuesday. +30% Experience, +30% Item Find");
				setDungeonsFromString(map.getOrDefault("tuDungeons", getDungeonsAsString()));
				setRaidsFromString(map.getOrDefault("tuRaids", getRaidsAsString()));
				difficulty = Integer.parseInt(map.getOrDefault("tuDifficulty", "" + difficulty));
				break;
			case WEDNESDAY:
				BHBot.log("Today is a Wednesday. +50% Experience");
				setDungeonsFromString(map.getOrDefault("weDungeons", getDungeonsAsString()));
				setRaidsFromString(map.getOrDefault("weRaids", getRaidsAsString()));
				difficulty = Integer.parseInt(map.getOrDefault("weDifficulty", "" + difficulty));
				break;
			case THURSDAY:
				BHBot.log("Today is a Thursday. +50% Capture Rate");
				setDungeonsFromString(map.getOrDefault("thDungeons", getDungeonsAsString()));
				setRaidsFromString(map.getOrDefault("thRaids", getRaidsAsString()));
				difficulty = Integer.parseInt(map.getOrDefault("thDifficulty", "" + difficulty));
				break;
			case FRIDAY:
				BHBot.log("Today is a Friday. +100% PVP Experience, +100% PVP Item Find");
				setDungeonsFromString(map.getOrDefault("frDungeons", getDungeonsAsString()));
				setRaidsFromString(map.getOrDefault("frRaids", getRaidsAsString()));
				difficulty = Integer.parseInt(map.getOrDefault("frDifficulty", "" + difficulty));
				break;
			case SATURDAY:
				BHBot.log("Today is a Saturday. +30% Experience, +30% Item Find, +30% Capture Rate");
				setDungeonsFromString(map.getOrDefault("saDungeons", getDungeonsAsString()));
				setRaidsFromString(map.getOrDefault("saRaids", getRaidsAsString()));
				difficulty = Integer.parseInt(map.getOrDefault("saDifficulty", "" + difficulty));
				break;
			case SUNDAY:
				BHBot.log("Today is a Sunday. +50% Item Find, +50% Gold Find");
				setDungeonsFromString(map.getOrDefault("suDungeons", getDungeonsAsString()));
				setRaidsFromString(map.getOrDefault("suRaids", getRaidsAsString()));
				difficulty = Integer.parseInt(map.getOrDefault("suDifficulty", "" + difficulty));
				break;
		}
		
		username = map.getOrDefault("username", username);
		password = map.getOrDefault("password", password);
		useHeadlessMode = !map.getOrDefault("headlessmode", useHeadlessMode ? "1" : "0").equals("0");
		restartAfterAdOfferTimeout = map.getOrDefault("restartAfterAdOfferTimeout", restartAfterAdOfferTimeout ? "1" : "0").equals("1");
		debugDetectionTimes = !map.getOrDefault("debugDetectionTimes", debugDetectionTimes ? "1" : "0").equals("0");
		hideWindowOnRestart = !map.getOrDefault("hideWindowOnRestart", hideWindowOnRestart ? "1" : "0").equals("0");
		resetTimersOnBattleEnd = map.getOrDefault("resetTimersOnBattleEnd", resetTimersOnBattleEnd ? "1" : "0").equals("1");
		
		doRaids = map.getOrDefault("doRaids", doRaids ? "1" : "0").equals("1");
		doDungeons = map.getOrDefault("doDungeons", doDungeons ? "1" : "0").equals("1");
		doTrials = map.getOrDefault("doTrials", doTrials ? "1" : "0").equals("1");
		doGauntlet = map.getOrDefault("doGauntlet", doGauntlet ? "1" : "0").equals("1");
		doPVP = map.getOrDefault("doPVP", doPVP ? "1" : "0").equals("1");
		doGVG = map.getOrDefault("doGVG", doGVG ? "1" : "0").equals("1");
		doInvasion = map.getOrDefault("doInvasion", doInvasion ? "1" : "0").equals("1");
		doAds = !map.getOrDefault("doAds", doAds ? "1" : "0").equals("0");
		
		costTickets = Integer.parseInt(map.getOrDefault("costTickets", "" + costTickets));
		costGVG = Integer.parseInt(map.getOrDefault("costGVG", "" + costGVG));
		costTokens = Byte.parseByte(map.getOrDefault("costTokens", "" + costTokens));
		costBadges = Integer.parseInt(map.getOrDefault("costBadges", "" + costBadges));
		
		minShards = Integer.parseInt(map.getOrDefault("minShards", "" + minShards));
		minTokens = Byte.parseByte(map.getOrDefault("minTokens", "" + minTokens));
		minEnergy = Short.parseShort(map.getOrDefault("minEnergy", "" + minEnergy));
		minTickets = Integer.parseInt(map.getOrDefault("minTickets", "" + minTickets));
		minBadges = Integer.parseInt(map.getOrDefault("minBadges", "" + minBadges));
		minBadgesGVG = Byte.parseByte(map.getOrDefault("minBadgesGVG", "" + minBadgesGVG));
		
		maxShards = Byte.parseByte(map.getOrDefault("maxShards", "" + maxShards));
		maxTokens = Byte.parseByte(map.getOrDefault("maxTokens", "" + maxTokens));
		maxEnergy = Short.parseShort(map.getOrDefault("maxEnergy", "" + maxEnergy));
		maxTickets = Byte.parseByte(map.getOrDefault("maxTickets", "" + maxTickets));
		maxBadges = Byte.parseByte(map.getOrDefault("maxBadges", "" + maxBadges));
		
		setStripsFromString(map.getOrDefault("pvpStrip", getStripsAsString()));
		
		autoConsume = map.getOrDefault("autoconsume", autoConsume ? "1" : "0").equals("1");
		setConsumablesFromString(map.getOrDefault("consumables", getConsumablesAsString()));
		
		pauseOnDisconnect = Integer.parseInt(map.getOrDefault("pauseOnDisconnect", "" + pauseOnDisconnect));
	}
	
	/**
	 * Loads settings from disk.
	 */
	public void load() {
		load(DEFAULT_SETTINGS_FILE);
	}
	
	/**
	 * Loads settings from disk.
	 */
	public void load(String file) {
		List<String> lines = Misc.readTextFile2(file);
		if (lines == null || lines.size() == 0)
			return;
		
		load(lines);
	}
}
