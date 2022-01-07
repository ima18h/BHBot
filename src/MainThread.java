import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.assertthat.selenium_shutterbug.core.Shutterbug;

public class MainThread implements Runnable {
	public static enum State {
		Loading("Loading..."),
		Main("Main screen"),
		Raid("Raid"),
		Trials("Trials"),
		Gauntlet("Gauntlet"),
		Dungeon("Dungeon"),
		WorldBoss("Dungeon"),
		PVP("PVP"),
		GVG("GVG"),
		Invasion("Invasion"),
		UnidentifiedDungeon("Unidentified dungeon"); // this one is used when we log in and we get a "You were recently disconnected from a dungeon. Do you want to continue the dungeon?" window
		
		private String name;
		
		private State(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
	}
	
	public static class ReturnResult {
		String msg;
		boolean needsRestart;
		
		public ReturnResult() {
		}
		
		public ReturnResult(String msg, boolean needsRestart) {
			this.msg = msg;
			this.needsRestart = needsRestart;
		}
		
		/**
		 * Returns error with need to restart.
		 */
		public static ReturnResult error(String msg) {
			return new ReturnResult(msg, true);
		}
		
		public static ReturnResult ok() {
			return new ReturnResult(null, false);
		}
	}
	
	/**
	 * Events that use badges as "fuel".
	 */
	public static enum BadgeEvent {
		None,
		GVG,
		Invasion;
	}
	
	public static enum EquipmentType {
		Mainhand("StripTypeMainhand"),
		Offhand("StripTypeOffhand"),
		Head("StripTypeHead"),
		Body("StripTypeBody"),
		Neck("StripTypeNeck"),
		Ring("StripTypeRing");
		
		private String cueName;
		
		private EquipmentType(String cueName) {
			this.cueName = cueName;
		}
		
		/**
		 * Returns equipment filter button cue (it's title cue actually)
		 */
		public Cue getCue() {
			return cues.get(cueName);
		}
		
		public int minPos() {
			return 2 + ordinal();
			
		}
		
		public int maxPos() {
			return Math.min(6 + ordinal(), 9);
		}
		
		public int getButtonPos() {
			return 6 + ordinal();
		}
		
		public static String letterToName(String s) {
			if (s.equals("m"))
				return "mainhand";
			else if (s.equals("o"))
				return "offhand";
			else if (s.equals("h"))
				return "head";
			else if (s.equals("b"))
				return "body";
			else if (s.equals("n"))
				return "neck";
			else if (s.equals("r"))
				return "ring";
			else
				return "unknown_item";
		}
		
		public static EquipmentType letterToType(String s) {
			if (s.equals("m"))
				return Mainhand;
			else if (s.equals("o"))
				return Offhand;
			else if (s.equals("h"))
				return Head;
			else if (s.equals("b"))
				return Body;
			else if (s.equals("n"))
				return Neck;
			else if (s.equals("r"))
				return Ring;
			else
				return null; // should not happen!
		}
	}
	
	public static enum StripDirection {
		StripDown,
		DressUp;
	}
	
	private static enum ConsumableType {
		EXP_MINOR("exp_minor", "ConsumableExpMinor"), // experience tome
		EXP_AVERAGE("exp_average", "ConsumableExpAverage"),
		EXP_MAJOR("exp_major", "ConsumableExpMajor"),
		
		ITEM_MINOR("item_minor", "ConsumableItemMinor"), // item find scroll
		ITEM_AVERAGE("item_average", "ConsumableItemAverage"),
		ITEM_MAJOR("item_major", "ConsumableItemMajor"),
		
		GOLD_MINOR("gold_minor", "ConsumableGoldMinor"), // item find scroll
		GOLD_AVERAGE("gold_average", "ConsumableGoldAverage"),
		GOLD_MAJOR("gold_major", "ConsumableGoldMajor"),
		
		SPEED_MINOR("speed_minor", "ConsumableSpeedMinor"), // speed kicks
		SPEED_AVERAGE("speed_average", "ConsumableSpeedAverage"),
		SPEED_MAJOR("speed_major", "ConsumableSpeedMajor");
		
		private String name;
		private String inventoryCue;
		
		private ConsumableType(String name, String inventoryCue) {
			this.name = name;
			this.inventoryCue = inventoryCue;
		}
		
		/**
		 * Returns name as it appears in e.g. settings.ini.
		 */
		public String getName() {
			return name;
		}
		
		public static ConsumableType getTypeFromName(String name) {
			for (ConsumableType type : ConsumableType.values())
				if (type.name.equals(name))
					return type;
			return null;
		}
		
		/**
		 * Returns image cue from inventory window
		 */
		public Cue getInventoryCue() {
			return cues.get(inventoryCue);
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	public static final int SECOND = 1000;
	public static final int MINUTE = 60 * SECOND;
	
	/**
	 * Counters for victories and defeats on various encounters.
	 */
	/*
	public static byte WBVictories = 0;
	public static byte WBDefeats = 0;
	public static byte WBVictoryPercentage = -1;
	
	public static byte GVGVictories = 0;
	public static byte GVGDefeats = 0;
	public static byte GVGVictoryPercentage = -1;
	
	public static byte PVPVictories = 0;
	public static byte PVPDefeats = 0;
	public static byte PVPVictoryPercentage = -1;
	*/
	
	//private static final int MAX_LAST_AD_OFFER_TIME = 18 * MINUTE; // after this time, restart() will get called since ads are not coming through anymore
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
	
	public static Map<String, Cue> cues = new HashMap<String, Cue>();
	
	public boolean finished = false;
	private int numFailedRestarts = 0; // in a row
	private final int MAX_NUM_FAILED_RESTARTS = 5;
	private final boolean QUIT_AFTER_MAX_FAILED_RESTARTS = false;
	public static WebDriver driver;
	public static WebElement game;
	public State state; // at which stage of the game/menu are we currently?
	public BufferedImage img; // latest screen capture
	
	long timeLastEnergyCheck = 0; // when did we check for energy the last time?
	final long ENERGY_CHECK_INTERVAL = 20 * MINUTE;
	long timeLastShardsCheck = 0; // when did we check for raid shards the last time?
	final long SHARDS_CHECK_INTERVAL = 30 * MINUTE;
	long timeLastTicketsCheck = 0; // when did we check for tickets the last time?
	final long TICKETS_CHECK_INTERVAL = 12 * MINUTE;
	long timeLastTokensCheck = 0; // when did we check for tokens the last time?
	final long TOKENS_CHECK_INTERVAL = 12 * MINUTE;
	long timeLastBadgesCheck = 0; // when did we check for badges the last time?
	final long BADGES_CHECK_INTERVAL = 12 * MINUTE;
	long timeLastConsumablesCheck = 0; // when did we check for bonuses (active consumables) the last time?
	final long CONSUMABLES_CHECK_INTERVAL = 11 * MINUTE;
	
	int raidType; // the raid we are in right now.
	
	public static long timeLastSettingsCheck = 0;
	public final long MAX_SETTINGS_RELOAD_TIME = 60 * MINUTE;
	
	public final long MAX_IDLE_TIME = 30 * MINUTE;
	
	// have we done the startup check?
	public boolean STARTUP_BADGE_CHECK = false;
	public boolean STARTUP_TICKET_CHECK = false;
	public boolean STARTUP_TOKEN_CHECK = false;
	/**
	 * Number of consecutive exceptions. We need to track it in order to detect crash loops that we must break by restarting the Chrome driver. Or else it could get into loop and stale.
	 */
	private int numConsecutiveException = 0;
	private final int MAX_CONSECUTIVE_EXCEPTIONS = 10;
	/**
	 * Amount of ads that were offered in main screen since last restart. We need it in order to do restart() after 2 ads, since sometimes ads won't get offered anymore after two restarts.
	 */
	public int numAdOffers = 0;
	/**
	 * Time when we got last ad offered. If it exceeds 15 minutes, then we should call restart() because ads are not getting through!
	 */
	public long timeLastAdOffer;
	
	/*
	public ChromeDriver createNewServer() {
		return new ChromeDriver();
	}
	*/
	
	public RemoteWebDriver getRemoteWebDriver() {
		return (RemoteWebDriver) driver;
	}
	
	public JavascriptExecutor getJS() {
		return (JavascriptExecutor) driver;
	}
	
	public static BufferedImage loadImage(String f) {
		BufferedImage img = null;
		try {
			img = ImageIO.read(new File(f));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return img;
	}
	
	private static void addCue(String name, BufferedImage im, Bounds bounds) {
		cues.put(name, new Cue(name, im, bounds));
	}
	
	public static void loadCues() {
		addCue("Main", loadImage("cues/cueMainScreen.png"), new Bounds(90, 5, 100, 10));
		
		addCue("Login", loadImage("cues/cueLogin.png"), new Bounds(270, 260, 330, 300)); // login window (happens seldom)
		
		addCue("AreYouThere", loadImage("cues/cueAreYouThere.png"), new Bounds(240, 245, 265, 260));
		addCue("Yes", loadImage("cues/cueYes.png"), null);
		
		addCue("Disconnected", loadImage("cues/cueDisconnected.png"), new Bounds(290, 230, 315, 250)); // cue for "You have been disconnected" popup
		addCue("Reconnect", loadImage("cues/cueReconnectButton.png"), new Bounds(320, 330, 400, 360)); // used with "You have been disconnected" dialog and also with the "maintenance" dialog
		addCue("Reload", loadImage("cues/cueReload.png"), new Bounds(320, 330, 360, 360)); // used in "There is a new update required to play" dialog (happens on Friday night)
		addCue("Maintenance", loadImage("cues/cueMaintenance.png"), new Bounds(230, 200, 320, 250)); // cue for "Bit Heroes is currently down for maintenance. Please check back shortly!"
		addCue("Loading", loadImage("cues/cueLoading.png"), new Bounds(315, 210, 330, 225)); // cue for "Loading" superimposed screen
		addCue("RecentlyDisconnected", loadImage("cues/cueRecentlyDisconnected.png"), new Bounds(220, 195, 255, 230)); // cue for "You were recently disconnected from a dungeon. Do you want to continue the dungeon?" window 
		addCue("UnableToConnect", loadImage("cues/cueUnableToConnect.png"), new Bounds(245, 235, 270, 250)); // happens when some error occurs for which the flash app is unable to connect to the server. We must simply click on the "Reconnect" button in this case!
		
		addCue("DailyRewards", loadImage("cues/cueDailyRewards.png"), new Bounds(260, 45, 285, 75));
		addCue("Claim", loadImage("cues/cueClaim.png"), null); // claim button, when daily rewards popup is open 
		addCue("Items", loadImage("cues/cueItems.png"), null); // used when we clicked "claim" on daily rewards popup. Used also with main menu ads.
		addCue("X", loadImage("cues/cueX.png"), null); // "X" close button used with claimed daily rewards popup
		addCue("WeeklyRewards", loadImage("cues/cueWeeklyRewards.png"), new Bounds(185, 95, 250, 185)); // used with reward for GVG/PVP/Gauntlet/Trial on Friday night (when day changes into Saturday)
		
		addCue("News", loadImage("cues/cueNewsPopup.png"), new Bounds(345, 60, 365, 85)); // news popup
		addCue("Close", loadImage("cues/cueClose.png"), null); // close button used with "News" popup, also when defeated in dungeon, etc.
		
		/*
		addCue("Ad", loadImage("cues/cueAd.png"), new Bounds(720, 100, 750, 410)); // main screen ad button cue. Note that sometimes it is higher up on the screen than other times (during GvG week, it will be higher up, above GvG icon)
		addCue("AdPopup", loadImage("cues/cueAdPopup.png"), null);
		addCue("Watch", loadImage("cues/cueWatch.png"), null); // used with ad main screen watch button. Does not work in dungeons (button is a bit different there - used Watch2 button there!)
		addCue("Watch2", loadImage("cues/cueWatch2.png"), null); // this is an alternative watch button. It is practically the same as the first one, but it has another shade of blue in the first row. Used with watching ads in dungeons (confirmed).
		addCue("AdInProgress", loadImage("cues/cueAdInProgress.png"), null); // we currently don't really need this cue
		addCue("AdFinished", loadImage("cues/cueAdFinished.png"), null); // we currently don't really need this cue
		addCue("Skip", loadImage("cues/cueSkip.png"), null);
		*/
		
		addCue("EnergyBar", loadImage("cues/cueEnergyBar.png"), new Bounds(390, 0, 420, 20));
		addCue("TicketBar", loadImage("cues/cueTicketBar.png"), new Bounds(540, 0, 770, 20));
		
		addCue("RaidButton", loadImage("cues/cueRaidButton.png"), new Bounds(0, 200, 40, 400));
		addCue("RaidPopup", loadImage("cues/cueRaidPopup.png"), new Bounds(300, 35, 340, 70));
		addCue("RaidSummon", loadImage("cues/cueRaidSummon.png"), new Bounds(480, 360, 540, 380));
		addCue("RaidLevel", loadImage("cues/cueRaidLevel.png"), new Bounds(320, 430, 480, 460)); // selected raid type button cue
		addCue("R1Only", loadImage("cues/cueR1Only.png"), new Bounds(180, 345, 240, 380)); // cue for R1 type selected when R2 (and R3) is not unlocked yet (in that case it won't show raid type selection buttons)
		
		addCue("Normal", loadImage("cues/cueNormal.png"), null);
		addCue("Hard", loadImage("cues/cueHard.png"), null);
		addCue("Heroic", loadImage("cues/cueHeroic.png"), null);
		addCue("Accept", loadImage("cues/cueAccept.png"), null);
		addCue("Cleared", loadImage("cues/cueCleared.png"), null); // used for example when raid has been finished
		addCue("Defeat", loadImage("cues/cueDefeat.png"), null); // used for example when you have been defeated in a dungeon. Also used when you have been defeated in a gauntlet.
		addCue("YesGreen", loadImage("cues/cueYesGreen.png"), null); // used for example when raid has been finished ("Cleared" popup)
		addCue("Persuade", loadImage("cues/cuePersuade.png"), null);
		addCue("SkeletonTreasure", loadImage("cues/cueSkeletonTreasure.png"), null); // skeleton treasure found in dungeons (it's a dialog/popup cue)
		addCue("SkeletonTreasureOpen", loadImage("cues/cueSkeletonTreasureOpen.png"), null);
		//addCue("AdTreasure", loadImage("cues/cueAdTreasure.png"), null); // ad treasure found in dungeons (it's a dialog/popup cue)
		addCue("Decline", loadImage("cues/cueDecline.png"), null); // decline skeleton treasure button (found in dungeons), also with video ad treasures (found in dungeons)
		addCue("Merchant", loadImage("cues/cueMerchant.png"), null); // cue for merchant dialog/popup
		
		addCue("TeamNotFull", loadImage("cues/cueTeamNotFull.png"), new Bounds(230, 200, 330, 250)); // warning popup when some friend left you and your team is not complete anymore
		addCue("TeamNotOrdered", loadImage("cues/cueTeamNotOrdered.png"), new Bounds(230, 190, 350, 250)); // warning popup when some guild member left and your GvG team is not complete anymore
		addCue("No", loadImage("cues/cueNo.png"), null); // cue for a blue "No" button used for example with "Your team is not full" dialog, or for "Replace consumable" dialog, etc. This is why we can't put concrete borders as position varies a lot.
		addCue("AutoTeam", loadImage("cues/cueAutoTeam.png"), null); // "Auto" button that automatically assigns team (in raid, GvG, ...)
		
		addCue("AutoOn", loadImage("cues/cueAutoOn.png"), new Bounds(740, 180, 785, 220)); // cue for auto pilot on
		addCue("AutoOff", loadImage("cues/cueAutoOff.png"), new Bounds(740, 180, 785, 220)); // cue for auto pilot off
		
		addCue("Trials", loadImage("cues/cueTrials.png"), new Bounds(0, 0, 40, 400)); // cue for trials button (note that as of 23.9.2017 they changed the button position to the right side of the screen and modified the glyph)
		addCue("Trials2", loadImage("cues/cueTrials2.png"), new Bounds(720, 0, 770, 400)); // an alternative cue for trials (flipped horizontally, located on the right side of the screen). Used since 23.9.2017.
		addCue("Gauntlet", loadImage("cues/cueGauntlet.png"), null); // cue for gauntlet button
		addCue("Gauntlet1", loadImage("cues/cueGauntlet1.png"), null);
		addCue("Play", loadImage("cues/cuePlay.png"), null); // cue for play button in trials/gauntlet window
		addCue("TokenBar", loadImage("cues/cueTokenBar.png"), null);
		addCue("CloseGreen", loadImage("cues/cueCloseGreen.png"), null); // close button used with "You have been defeated" popup in gauntlet and also "Victory" window in gauntlet
		addCue("Victory", loadImage("cues/cueVictory.png"), null); // victory window cue found upon completing gauntlet
		
		addCue("Quest", loadImage("cues/cueQuest.png"), new Bounds(0, 0, 40, 40)); // cue for quest (dungeons) button
		addCue("ZonesButton", loadImage("cues/cueZonesButton.png"), new Bounds(105, 60, 125, 75));
		addCue("Zone1", loadImage("cues/cueZone1.png"), null);
		addCue("Zone2", loadImage("cues/cueZone2.png"), null);
		addCue("Zone3", loadImage("cues/cueZone3.png"), null);
		addCue("Zone4", loadImage("cues/cueZone4.png"), null);
		addCue("Zone5", loadImage("cues/cueZone5.png"), null);
		addCue("Zone6", loadImage("cues/cueZone6.png"), null);
		addCue("Zone7", loadImage("cues/cueZone7.png"), null);
		addCue("RightArrow", loadImage("cues/cueRightArrow.png"), null); // arrow used in quest screen to change zone
		addCue("LeftArrow", loadImage("cues/cueLeftArrow.png"), null); // arrow used in quest screen to change zone
		addCue("Enter", loadImage("cues/cueEnter.png"), new Bounds(400, 400, 460, 470)); // "Enter" button found on d4 window
		addCue("NotEnoughEnergy", loadImage("cues/cueNotEnoughEnergy.png"), new Bounds(260, 210, 290, 235)); // "Not enough energy" popup cue
		
		addCue("PVP", loadImage("cues/cuePVP.png"), new Bounds(0, 70, 40, 110)); // PVP icon in main screen
		addCue("Fight", loadImage("cues/cueFight.png"), null); // fight button in PVP window
		addCue("VictoryPopup", loadImage("cues/cueVictoryPopup.png"), null); // victory popup that appears in PVP after you have successfully completed it (needs to be closed). Also found in dungeons after you've completed an encounter (and hence probably also in trials, but not in gauntlet - that one has a different 'Victory' window!)
		addCue("PVPWindow", loadImage("cues/cuePVPWindow.png"), null); // PVP window cue
		
		addCue("DialogRight", loadImage("cues/cueDialogRight.png"), null); // cue for the dialog window (when arrow is at the right side of the window)
		addCue("DialogLeft", loadImage("cues/cueDialogLeft.png"), null); // cue for the dialog window (when arrow is at the left side of the window)
		
		//addCue("1Xspeed", loadImage("cues/cue1Xspeed.png"), new Bounds(0, 250, 40, 480));
		//addCue("3Xspeed", loadImage("cues/cue3Xspeed.png"), new Bounds(0, 250, 40, 480));
		
		// GVG related:
		addCue("GVG", loadImage("cues/cueGVG.png"), new Bounds(720, 270, 770, 480)); // main GVG button cue
		addCue("BadgeBar", loadImage("cues/cueBadgeBar.png"), null);
		addCue("GVGWindow", loadImage("cues/cueGVGWindow.png"), new Bounds(260, 90, 280, 110)); // GVG window cue
		
		addCue("InGamePM", loadImage("cues/cueInGamePM.png"), new Bounds(450, 330, 530, 380)); // note that the guild window uses the same cue! That's why it's important that user doesn't open guild window while bot is working!
		
		addCue("TrialsOrGauntletWindow", loadImage("cues/cueTrialsOrGauntletWindow.png"), new Bounds(300, 30, 510, 105)); // cue for a trials/gauntlet window
		addCue("Difficulty", loadImage("cues/cueDifficulty.png"), new Bounds(450, 330, 640, 450)); // selected difficulty in trials/gauntlet window
		addCue("DifficultyDisabled", loadImage("cues/cueDifficultyDisabled.png"), new Bounds(450, 330, 640, 450)); // selected difficulty in trials/gauntlet window (disabled - because game is still fetching data from server)
		addCue("SelectDifficulty", loadImage("cues/cueSelectDifficulty.png"), new Bounds(400, 260, 0, 0)/*not exact bounds... the lower-right part of screen!*/); // select difficulty button in trials/gauntlet
		addCue("DifficultyDropDown", loadImage("cues/cueDifficultyDropDown.png"), new Bounds(260, 50, 550, 125)); // difficulty drop down menu cue
		addCue("DropDownUp", loadImage("cues/cueDropDownUp.png"), null); // up arrow in difficulty drop down menu (found in trials/gauntlet, for example)
		addCue("DropDownDown", loadImage("cues/cueDropDownDown.png"), null); // down arrow in difficulty drop down menu (found in trials/gauntlet, for example)
		addCue("Cost", loadImage("cues/cueCost.png"), new Bounds(400, 150, 580, 240)); // used both for PvP and Gauntlet/Trials costs. Note that bounds are very wide, because position of this cue in PvP is different from that in Gauntlet/Trials!
		addCue("SelectCost", loadImage("cues/cueSelectCost.png"), new Bounds(555, 170, 595, 205)); // cue for select cost found in both PvP and Gauntlet/Trials windows. Note that bounds are wide, because position of this cue in PvP is different from that in Gauntlet/Trials!
		addCue("CostDropDown", loadImage("cues/cueCostDropDown.png"), new Bounds(260, 45, 320, 70)); // cue for cost selection drop down window
		
		// used for trial/gauntlet numbers i think...
		addCue("0", loadImage("cues/cue0.png"), null);
		addCue("1", loadImage("cues/cue1.png"), null);
		addCue("2", loadImage("cues/cue2.png"), null);
		addCue("3", loadImage("cues/cue3.png"), null);
		addCue("4", loadImage("cues/cue4.png"), null);
		addCue("5", loadImage("cues/cue5.png"), null);
		addCue("6", loadImage("cues/cue6.png"), null);
		addCue("7", loadImage("cues/cue7.png"), null);
		addCue("8", loadImage("cues/cue8.png"), null);
		addCue("9", loadImage("cues/cue9.png"), null);
		
		// PvP strip related:
		addCue("StripScrollerTopPos", loadImage("cues/strip/cueStripScrollerTopPos.png"), new Bounds(525, 140, 540, 370));
		addCue("StripEquipped", loadImage("cues/strip/cueStripEquipped.png"), new Bounds(460, 175, 485, 205)); // the little "E" icon upon an equipped item (the top-left item though, we want to detect just that one)
		addCue("StripItemsTitle", loadImage("cues/strip/cueStripItemsTitle.png"), new Bounds(335, 70, 360, 80));
		addCue("StripSelectorButton", loadImage("cues/strip/cueStripSelectorButton.png"), new Bounds(450, 115, 465, 130));
		// filter titles:
		addCue("StripTypeBody", loadImage("cues/strip/cueStripTypeBody.png"), new Bounds(460, 125, 550, 140));
		addCue("StripTypeHead", loadImage("cues/strip/cueStripTypeHead.png"), new Bounds(460, 125, 550, 140));
		addCue("StripTypeMainhand", loadImage("cues/strip/cueStripTypeMainhand.png"), new Bounds(460, 125, 550, 140));
		addCue("StripTypeOffhand", loadImage("cues/strip/cueStripTypeOffhand.png"), new Bounds(460, 125, 550, 140));
		addCue("StripTypeNeck", loadImage("cues/strip/cueStripTypeNeck.png"), new Bounds(460, 125, 550, 140));
		addCue("StripTypeRing", loadImage("cues/strip/cueStripTypeRing.png"), new Bounds(460, 125, 550, 140));
		
		// consumables management related:
		addCue("BonusExp", loadImage("cues/cueBonusExp.png"), new Bounds(100, 455, 370, 485)); // consumable icon in the main menu (when it's being used)
		addCue("BonusItem", loadImage("cues/cueBonusItem.png"), new Bounds(100, 455, 370, 485));
		addCue("BonusGold", loadImage("cues/cueBonusGold.png"), new Bounds(100, 455, 370, 485));
		addCue("BonusSpeed", loadImage("cues/cueBonusSpeed.png"), new Bounds(100, 455, 370, 485));
		addCue("ConsumableExpMinor", loadImage("cues/cueConsumableExpMinor.png"), null); // consumable icon in the inventory
		addCue("ConsumableExpAverage", loadImage("cues/cueConsumableExpAverage.png"), null);
		addCue("ConsumableExpMajor", loadImage("cues/cueConsumableExpMajor.png"), null);
		addCue("ConsumableItemMinor", loadImage("cues/cueConsumableItemMinor.png"), null);
		addCue("ConsumableItemAverage", loadImage("cues/cueConsumableItemAverage.png"), null);
		addCue("ConsumableItemMajor", loadImage("cues/cueConsumableItemMajor.png"), null);
		addCue("ConsumableSpeedMinor", loadImage("cues/cueConsumableSpeedMinor.png"), null);
		addCue("ConsumableSpeedAverage", loadImage("cues/cueConsumableSpeedAverage.png"), null);
		addCue("ConsumableSpeedMajor", loadImage("cues/cueConsumableSpeedMajor.png"), null);
		addCue("ConsumableGoldMinor", loadImage("cues/cueConsumableGoldMinor.png"), null);
		addCue("ConsumableGoldAverage", loadImage("cues/cueConsumableGoldAverage.png"), null);
		addCue("ConsumableGoldMajor", loadImage("cues/cueConsumableGoldMajor.png"), null);
		addCue("ScrollerAtBottom", loadImage("cues/cueScrollerAtBottom.png"), null); // cue for scroller being at the bottom-most position (we can't scroll down more than this)
		addCue("ConsumableTitle", loadImage("cues/cueConsumableTitle.png"), new Bounds(280, 100, 310, 180)); // cue for title of the window that pops up when we want to consume a consumable. Note that vertical range is big here since sometimes is higher due to greater window size and sometimes is lower.
		addCue("FilterConsumables", loadImage("cues/cueFilterConsumables.png"), new Bounds(460, 125, 550, 140)); // cue for filter button name
		addCue("LoadingInventoryIcon", loadImage("cues/cueLoadingInventoryIcon.png"), null); // cue for loading animation for the icons inside inventory
		
		// invasion related:
		addCue("Invasion", loadImage("cues/cueInvasion.png"), new Bounds(720, 270, 770, 480)); // main Invasion button cue
		addCue("InvasionWindow", loadImage("cues/cueInvasionWindow.png"), new Bounds(260, 90, 280, 110)); // GVG window cue
		
		// Familiar related
		// addCue("200Bribe", loadImage("cues/familiars/200Bribe.png"), new Bounds(535, 240, 700, 330));
		// addCue("400Bribe", loadImage("cues/familiars/400Bribe.png"), new Bounds(535, 240, 700, 330));
		// addCue("800Bribe", loadImage("cues/familiars/800Bribe.png"), new Bounds(535, 240, 700, 330));
		addCue("4000Persuade", loadImage("cues/familiars/4000Persuade.png"), null);
		addCue("Bribe", loadImage("cues/familiars/Bribe.png"), null);
		
		// World boss related
		// victory = closeGreen, defeat=close
		// after closeGreen, have to press X with summon button, blue summon button=summonButton.png
		addCue("WBButton", loadImage("cues/worldBoss/button.png"), null);
		addCue("WBSummonButton", loadImage("cues/worldBoss/summonButton.png"), null);
		addCue("WBSummonButtonGreen0", loadImage("cues/worldBoss/summonButtonGreen0.png"), null);
		addCue("WBSummonButtonGreen1", loadImage("cues/worldBoss/summonButtonGreen1.png"), null);
		addCue("WBDown", loadImage("cues/worldBoss/down.png"), null);
		addCue("WB1", loadImage("cues/worldBoss/1.png"), null);
		addCue("WB2", loadImage("cues/worldBoss/2.png"), null);
		addCue("WB3", loadImage("cues/worldBoss/3.png"), null);
		addCue("WBStart", loadImage("cues/worldBoss/start.png"), null);
		//addCue("WBConfirm", loadImage("cues/worldBoss/confirm.png"), null);
		addCue("Refresh", loadImage("cues/worldBoss/refresh.png"), null);
		addCue("Join", loadImage("cues/worldBoss/join.png"), null);
		addCue("Ready", loadImage("cues/worldBoss/ready.png"), null);
		addCue("Netherworld", loadImage("cues/worldBoss/netherworld.png"), null);
		
		// manual combat related. only settings stuff now.
		// press down 26 times to get to bottom. or keep holding for like 4 seconds
		/*
		addCue("settingsButton", loadImage("cues/manualCombat/settingsButton.png"), null);
		addCue("settingsDown", loadImage("cues/manualCombat/settingsDown.png"), null);
		addCue("settingsIgnoreShrines", loadImage("cues/manualCombat/ignoreShrines.png"), null);
		addCue("settingsIgnoreBoss", loadImage("cues/manualCombat/ignoreBoss.png"), null);
		*/
	}
	
	public static void connectDriver() throws MalformedURLException {
		ChromeOptions options = new ChromeOptions();
		// https://sites.google.com/a/chromium.org/chromedriver/capabilities
		//options.addArguments("user-data-dir=./chrome_profile_test"); // will create this profile folder where chromedriver.exe is located!
		options.addArguments("user-data-dir=./chrome_profile"); // will create this profile folder where chromedriver.exe is located!
		//***options.addArguments("--no-startup-window");
		//***options.addArguments("--silent-launch");
		
		if (BHBot.settings.useHeadlessMode) {
			options.setBinary("C:/Users/Imad/AppData/Local/Google/Chrome SxS/Application/chrome.exe");
			
			// https://sites.google.com/a/chromium.org/chromedriver/capabilities
			
			options.addArguments("--headless");
			//options.addArguments("--disable-gpu"); // in future versions of Chrome this flag will not be needed
			
			/*
			options.addArguments("--disable-plugins");
			options.addArguments("--disable-internal-flash");
			options.addArguments("--disable-plugins-discovery");
			*/
			//options.addArguments("--disable-bundled-ppapi-flash");
			
			
			options.addArguments("--always-authorize-plugins");
			options.addArguments("--allow-outdated-plugins");
			options.addArguments("--allow-file-access-from-files");
			options.addArguments("--allow-running-insecure-content");
			options.addArguments("--disable-translate");
			options.addArguments("-�allow-webui-compositing"); // https://adestefawp.wordpress.com/software/chromium-command-line-switches/
			options.addArguments("-�ppapi-flash-in-process");
			
			options.addArguments("--use-fake-device-for-media-stream");
			options.addArguments("--disable-web-security");
			
			
			options.setExperimentalOption("excludeSwitches", Arrays.asList("disable-component-update", "disable-default-apps"));
			
			//options.setExperimentalOption("#run-all-flash-in-allow-mode", Arrays.asList("Enabled"));
			//options.setExperimentalOption("#run-all-flash-in-allow-mode", "Enabled");
			
			Map<String, Object> prefs = new HashMap<>();
			prefs.put("run-all-flash-in-allow-mode", Boolean.valueOf(true));
			prefs.put("profile.run_all_flash_in_allow_mode", Boolean.valueOf(true));
			options.setExperimentalOption("prefs", prefs);
			
			//options.addExtensions(new File("C:/Users/Betalord/AppData/Local/Google/Chrome SxS/Application/chrome_profile_test/PepperFlash/26.0.0.137/pepflashplayer.dll"));
			//options.addExtensions(new File("C:/Users/Betalord/AppData/Local/Google/Chrome SxS/User Data/PepperFlash/26.0.0.137/pepflashplayer.dll"));
			
			//options.addArguments("--remote-debugging-port=9222"); // this doesn't work because ChromeDriver uses dubuging port internally. Read about it here: https://bugs.chromium.org/p/chromedriver/issues/detail?id=878#c16
		}
		
		//options.addArguments("--no-startup-window"); // does not work with WebDriver. Read about it here: https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/5351
		
		
		//options.addArguments("--headless");
		//options.addArguments("--disable-gpu");
		options.addArguments("--mute-audio"); // found this solution here: https://stackoverflow.com/questions/39392479/how-to-mute-all-sounds-in-chrome-webdriver-with-selenium/39392601#39392601
		
		//***ChromeDriverService chromeDriverService = ChromeDriverService.createDefaultService();
		//***chromeDriverService.CHROME_DRIVER_SILENT_OUTPUT_PROPERTY
		
		DesiredCapabilities capabilities = DesiredCapabilities.chrome();
		capabilities.setCapability(ChromeOptions.CAPABILITY, options);
		driver = new RemoteWebDriver(new URL("http://" + BHBot.chromeDriverAddress), capabilities);
		//driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
	}
	
	// http://www.qaautomationsimplified.com/selenium/selenium-webdriver-get-cookies-from-an-existing-session-and-add-those-to-a-newly-instantiated-webdriver-browser-instance/
	// http://www.guru99.com/handling-cookies-selenium-webdriver.html
	public static void saveCookies() {
		// create file named Cookies to store Login Information		
		File file = new File("Cookies.data");
		try {
			// Delete old file if exists
			file.delete();
			file.createNewFile();
			FileWriter fileWrite = new FileWriter(file);
			BufferedWriter Bwrite = new BufferedWriter(fileWrite);
			// loop for getting the cookie information 		
			for (Cookie ck : driver.manage().getCookies()) {
				Bwrite.write((ck.getName() + ";" + ck.getValue() + ";" + ck.getDomain() + ";" + ck.getPath() + ";" + (ck.getExpiry() == null ? 0 : ck.getExpiry().getTime()) + ";" + ck.isSecure()));
				Bwrite.newLine();
			}
			Bwrite.flush();
			Bwrite.close();
			fileWrite.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		BHBot.log("Cookies saved to disk.");
	}
	
	public static void loadCookies() {
		try {
			File file = new File("Cookies.data");
			FileReader fileReader = new FileReader(file);
			BufferedReader Buffreader = new BufferedReader(fileReader);
			String strline;
			while ((strline = Buffreader.readLine()) != null) {
				StringTokenizer token = new StringTokenizer(strline, ";");
				while (token.hasMoreTokens()) {
					String name = token.nextToken();
					String value = token.nextToken();
					String domain = token.nextToken();
					String path = token.nextToken();
					Date expiry = null;
					
					String val;
					if (!(val = token.nextToken()).equals("null")) {
						new Date(Long.parseLong(val));
					}
					Boolean isSecure = Boolean.valueOf(token.nextToken());
					Cookie ck = new Cookie(name, value, domain, path, expiry, isSecure);
					try {
						driver.manage().addCookie(ck); // This will add the stored cookie to your current session
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			Buffreader.close();
			fileReader.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		BHBot.log("Cookies loaded.");
	}
	
	public void hideBrowser() {
		driver.manage().window().setPosition(new Point(-10000, 0)); // just to make sure
		BHBot.log("Chrome window has been hidden.");
	}
	
	public void showBrowser() {
		driver.manage().window().setPosition(new Point(0, 0));
		BHBot.log("Chrome window has been restored.");
	}
	
	private void dumpCrashLog() {
		// save screen shot:
		String file = saveGameScreen("crash");
		
		// save stack trace:
		Misc.saveTextFile(file.substring(0, file.length() - 4) + ".txt", Misc.getStackTrace());
	}
	
	public void restart() {
		restart(true); // assume emergency restart
	}
	
	/**
	 * @param emergency true in case something bad happened (some kind of an error for which we had to do a restart)
	 */
	public void restart(boolean emergency) {
		// take emergency screenshot (which will have the developer to debug the problem):
		if (emergency) {
			BHBot.log("Doing driver emergency restart...");
			dumpCrashLog();
		} else {
			BHBot.log("Doing driver restart...");
		}
		
		try {
			driver.quit();
		} catch (Exception e) {e.printStackTrace();
		}
		
		// disable some annoying INFO messages:
		Logger.getLogger("").setLevel(Level.WARNING);
		
		try {
			connectDriver();
			if (BHBot.settings.hideWindowOnRestart)
				hideBrowser();
			driver.navigate().to("http://www.kongregate.com/games/Juppiomenz/bit-heroes");
			//driver.navigate().to("chrome://flags/#run-all-flash-in-allow-mode");
			//driver.navigate().to("chrome://settings/content");
			//BHBot.processCommand("shot");
			game = driver.findElement(By.id("game"));
		} catch (Exception e) {
			e.printStackTrace();
			
			if (e instanceof org.openqa.selenium.NoSuchElementException)
				BHBot.log("Problem: web element with id 'game' not found!");
			if (e instanceof MalformedURLException)
				BHBot.log("Problem: malformed url detected!");
			
			numFailedRestarts++;
			if (QUIT_AFTER_MAX_FAILED_RESTARTS && numFailedRestarts > MAX_NUM_FAILED_RESTARTS) {
				BHBot.log("Something went wrong with driver restart. Number of restarts exceeded " + MAX_NUM_FAILED_RESTARTS + ", this is why I'm aborting...");
				finished = true;
				return;
			} else {
				BHBot.log("Something went wrong with driver restart. Will retry in a few minutes... (sleeping)");
				sleep(5 * MINUTE);
				restart();
				return;
			}
		}
		
		detectSignInFormAndHandleIt(); // just in case (happens seldom though)
		
		int vw = (int) (0 + (Long) (((JavascriptExecutor) driver).executeScript("return window.outerWidth - window.innerWidth + arguments[0];", game.getSize().width)));
		int vh = (int) (0 + (Long) (((JavascriptExecutor) driver).executeScript("return window.outerHeight - window.innerHeight + arguments[0];", game.getSize().height)));
		vw += 70; // compensate for scrollbars
		vh += 50; // compensate for scrollbars
		driver.manage().window().setSize(new Dimension(vw, vh));
		scrollGameIntoView();
		
		int counter = 0;
		boolean restart = false;
		while (true) {
			try {
				detectLoginFormAndHandleIt();
			} catch (Exception e) {
				counter++;
				if (counter > 20) {
					e.printStackTrace();
					BHBot.log("Error: <" + e.getMessage() + "> while trying to detect and handle login form. Restarting...");
					restart = true;
					break;
				}
				
				sleep(10 * SECOND);
				continue;
			}
			break;
		}
		if (restart) {
			restart();
			return;
		}
		
		BHBot.log("Driver is up and running.");
		//BHBot.log("Session id is: " + driver.getSessionId());
		BHBot.log("Window handle is: " + driver.getWindowHandle());
		
		state = State.Loading;
		BHBot.scheduler.resetIdleTime();
		numAdOffers = 0; // reset ad offers counter
		timeLastAdOffer = Misc.getTime();
		BHBot.scheduler.resume(); // in case it was paused
		
		numFailedRestarts = 0; // must be last line in this method!
	}
	
	//@TODO shares code with the restart() method
	public void startup() {
		// disable some annoying INFO messages:
		Logger.getLogger("").setLevel(Level.WARNING);
		
		try {
			connectDriver();
			if (BHBot.settings.hideWindowOnRestart)
				hideBrowser();
			driver.navigate().to("http://www.kongregate.com/games/Juppiomenz/bit-heroes");
			//driver.navigate().to("chrome://flags/#run-all-flash-in-allow-mode");
			//driver.navigate().to("chrome://settings/content");
			//BHBot.processCommand("shot");
			game = driver.findElement(By.id("game"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		int vw = (int) (0 + (Long) (((JavascriptExecutor) driver).executeScript("return window.outerWidth - window.innerWidth + arguments[0];", game.getSize().width)));
		int vh = (int) (0 + (Long) (((JavascriptExecutor) driver).executeScript("return window.outerHeight - window.innerHeight + arguments[0];", game.getSize().height)));
		vw += 70; // compensate for scrollbars
		vh += 50; // compensate for scrollbars
		driver.manage().window().setSize(new Dimension(vw, vh));
		scrollGameIntoView();
		
		state = State.Loading;
		BHBot.scheduler.resetIdleTime();
	}
	
	private void scrollGameIntoView() {
		/*
		 * Bellow code doesn't work well (it does not scroll horizontally):
		 * 
		 * ((JavascriptExecutor)driver).executeScript("arguments[0].scrollIntoView(true);", game);
		 */
		Actions actions = new Actions(driver);
		actions.moveToElement(game);
		actions.perform();
	}
	
	public void run() {
		BHBot.log("Welcome...");
		startup();
		
		while (!finished) {
			BHBot.scheduler.backupIdleTime();
			try {
				// do some sleeping at the start of loop:
				sleep(2 * SECOND);
				BHBot.scheduler.process();
				if (BHBot.scheduler.isPaused()) continue;
				
				if (Misc.getTime() - BHBot.scheduler.getIdleTime() > MAX_IDLE_TIME) {
					BHBot.log("Idle time exceeded... perhaps caught in a loop? Restarting... (state=" + state + ")");
					restart();
					continue;
				}
				BHBot.scheduler.resetIdleTime();
				
				moveMouseAway(); // just in case. Sometimes we weren't able to claim daily reward because mouse was in center and popup window obfuscated the claim button (see screenshot of that error!)
				readScreen();
				MarvinSegment seg;
				
				seg = detectCue(cues.get("UnableToConnect"));
				if (seg != null) {
					BHBot.log("'Unable to connect' dialog detected. Reconnecting...");
					seg = detectCue(cues.get("Reconnect"), 4 * SECOND);
					clickOnSeg(seg);
					sleep(4 * SECOND);
					state = State.Loading;
					continue;
				}
				
				// check for "Bit Heroes is currently down for maintenance. Please check back shortly!" window:
				seg = detectCue(cues.get("Maintenance"));
				if (seg != null) {
					seg = detectCue(cues.get("Reconnect"), 4 * SECOND);
					clickOnSeg(seg);
					BHBot.log("Maintenance dialog dismissed.");
					sleep(4 * SECOND);
					state = State.Loading;
					continue;
				}
				
				// check for "You have been disconnected" dialog:
				seg = detectCue(cues.get("Disconnected"));
				if (seg != null) {
					if (BHBot.scheduler.isUserInteracting || BHBot.scheduler.dismissReconnectOnNextIteration) {
						BHBot.scheduler.isUserInteracting = false;
						BHBot.scheduler.dismissReconnectOnNextIteration = false;
						seg = detectCue(cues.get("Reconnect"), 4 * SECOND);
						clickOnSeg(seg);
						BHBot.log("Disconnected dialog dismissed (reconnecting).");
						sleep(4 * SECOND);
						state = State.Loading;
						continue;
					} else {
						BHBot.scheduler.isUserInteracting = true;
						// probably user has logged in, that's why we got disconnected. Lets leave him alone for some time and then resume!
						BHBot.log("Disconnect has been detected. Probably due to user interaction. Sleeping for " + Misc.millisToHumanForm(BHBot.settings.pauseOnDisconnect) + "...");
						BHBot.scheduler.pause(BHBot.settings.pauseOnDisconnect);
						state = State.Loading;
						continue;
					}
				}
				
				BHBot.scheduler.dismissReconnectOnNextIteration = false; // must be done after checking for "Disconnected" dialog!
				
				// check for "There is a new update required to play" and click on "Reload" button:
				seg = detectCue(cues.get("Reload"));
				if (seg != null) {
					clickOnSeg(seg);
					BHBot.log("Update dialog dismissed.");
					sleep(4 * SECOND);
					state = State.Loading;
					continue;
				}
				
				// close any PMs:
				handlePM();
				
				// check for "Are you still there?" popup:
				seg = detectCue(cues.get("AreYouThere"));
				if (seg != null) {
					BHBot.scheduler.restoreIdleTime();
					seg = detectCue(cues.get("Yes"), 2 * SECOND);
					if (seg != null)
						clickOnSeg(seg);
					else {
						BHBot.log("Problem: 'Are you still there?' popup detected, but 'Yes' button not detected. Ignoring...");
						continue;
					}
					sleep(2 * SECOND);
					continue; // skip other stuff, we must first get rid of this popup!
				}
				
				// check for "News" popup:
				seg = detectCue(cues.get("News"));
				if (seg != null) {
					seg = detectCue(cues.get("Close"), 2 * SECOND);
					clickOnSeg(seg);
					BHBot.log("News popup dismissed.");
					sleep(2 * SECOND);
					
					continue;
				}
				
				// check for daily rewards popup:
				seg = detectCue(cues.get("DailyRewards"));
				if (seg != null) {
					seg = detectCue(cues.get("Claim"), 4 * SECOND);
					if (seg != null)
						clickOnSeg(seg);
					else {
						BHBot.log("Problem: 'Daily reward' popup detected, however could not detect the 'claim' button. Restarting...");
						restart();
						continue; // may happen every while, rarely though
					}
					
					readScreen(3 * SECOND);
					seg = detectCue(cues.get("Items"), SECOND);
					if (seg == null) {
						// we must terminate this thread... something happened that should not (unexpected). We must restart the thread!
						BHBot.log("Error: there is no 'Items' dialog open upon clicking on the 'Claim' button. Restarting...");
						restart();
						continue;
					}
					seg = detectCue(cues.get("X"));
					clickOnSeg(seg);
					BHBot.log("Daily reward claimed successfully.");
					sleep(2 * SECOND);
					
					continue;
				}
				
				// check for weekly (GvG, PvP, Trial, Gauntlet) rewards popup (and also for rewards in dungeons, which get auto-closed though):
				// (note that several, 2 or even 3 such popups may open one after another)
				seg = detectCue(cues.get("WeeklyRewards"));
				if (seg != null) {
					// hopefully we won't close some other important window that has the same fingerprint! (stuff that you find in dungeons has same fingerprint, but perhaps some other dialogs too...)
					seg = detectCue(cues.get("X"), 4 * SECOND);
					if (seg != null)
						clickOnSeg(seg);
					else if (state == State.Loading || state == State.Main) {
						BHBot.log("Problem: 'Weekly reward' popup detected, however could not detect the close ('X') button. Restarting...");
						restart();
						continue;
					}
					
					if (state == State.Loading || state == State.Main) { // inform about weekly reward only if we're not in a dungeon (in a dungeon it will close the normal reward popup)
						BHBot.log("Weekly reward claimed successfully.");
						saveGameScreen("weekly_reward");
					}
					sleep(2 * SECOND);
					
					continue;
				}
				
				// check for "recently disconnected" popup:
				seg = detectCue(cues.get("RecentlyDisconnected"));
				if (seg != null) {
					seg = detectCue(cues.get("YesGreen"), 2 * SECOND);
					if (seg == null) {
						BHBot.log("Error: detected 'recently disconnected' popup but could not find 'Yes' button. Restarting...");
						restart();
						continue;
					}
					
					clickOnSeg(seg);
					state = State.UnidentifiedDungeon; // we are not sure what type of dungeon we are doing
					BHBot.log("'You were recently in a dungeon' dialog detected and confirmed. Resuming dungeon...");
					sleep(10 * SECOND);
					continue;
				}
				
				// process dungeons of any kind (if we are in any):
				if (state == State.Dungeon || state == State.Raid || state == State.WorldBoss || state == State.Trials || state == State.Gauntlet || state == State.PVP || state == State.GVG || state == State.Invasion || state == State.UnidentifiedDungeon) {
					processDungeon();
					continue;
				}
				
				// check if we are in the main menu:
				seg = detectCue(cues.get("Main"));
				if (seg != null) {
					state = State.Main;
					
					// check for ads:
					/*
					if (BHBot.settings.doAds) {
						seg = detectCue(cues.get("Ad"));
						if (seg != null) {
							numAdOffers++; // increased ads offered counter
							timeLastAdOffer = Misc.getTime();
							clickOnSeg(seg);
							
							readScreen();
							seg = detectCue(cues.get("AdPopup"), 4 * SECOND);
							if (seg == null) {
								// we must terminate this thread... something happened that should not (unexpected). We must restart the thread!
								BHBot.log("Error: there is no 'Ad popup' dialog open upon clicking on the 'Ad' button. Restarting...");
								restart();
								continue;
							}
							
							seg = detectCue(cues.get("Watch"), 2 * SECOND);
							if (seg == null) {
								// we must terminate this thread... something happened that should not (unexpected). We must restart the thread!
								BHBot.log("Error: there is no 'Watch' button in the watch ad dialog. Restarting...");
								restart();
								continue;
							}
							clickOnSeg(seg);
							
							sleep(40 * SECOND);
							
							ReturnResult result = waitForAdAndCloseIt(true);
							
							if (result.needsRestart) {
								Misc.log("Error: " + result.msg + " Restarting...");
								restart();
								continue;
							}
							
							continue;
						} else { // no ad offer detected
							if (BHBot.settings.restartAfterAdOfferTimeout && Misc.getTime() - timeLastAdOffer > MAX_LAST_AD_OFFER_TIME) {
								BHBot.log("Problem detected: Last ad has been offered longer ago than expected. Restarting...");
								timeLastAdOffer = Misc.getTime(); // reset it
								restart(false);
								continue;
							}
						}
					} // adds
					*/
					
					// check for bonuses:
					if (BHBot.settings.autoConsume && (Misc.getTime() - timeLastConsumablesCheck > CONSUMABLES_CHECK_INTERVAL)) {
						timeLastConsumablesCheck = Misc.getTime();
						handleConsumables();
					}
					
					// check for shards:
					if ((BHBot.scheduler.doRaidImmediately || (BHBot.settings.doRaids && Misc.getTime() - timeLastShardsCheck > SHARDS_CHECK_INTERVAL)) && (STARTUP_BADGE_CHECK && STARTUP_TICKET_CHECK && STARTUP_TOKEN_CHECK)) {
						timeLastShardsCheck = Misc.getTime();
						seg = detectCue(cues.get("RaidButton"));
						if (seg == null) { // if null, then raid button is transparent meaning that raiding is not enabled (we have not achieved it yet, for example)
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						clickOnSeg(seg);
						
						readScreen(2 * SECOND);
						seg = detectCue(cues.get("RaidPopup")); // wait until the raid window opens
						if (seg == null) {
							BHBot.log("Error: attempt at opening raid window failed. No window cue detected. Ignoring...");
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						
						int shards = getShards();
						BHBot.log("Shards: " + shards);
						
						if (shards == -1) { // error
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						
						if (!BHBot.scheduler.doRaidImmediately && (shards - 1 < BHBot.settings.minShards)) {
							if (BHBot.scheduler.doRaidImmediately)
								BHBot.scheduler.doRaidImmediately = false; // reset it
							
							seg = detectCue(cues.get("X"));
							clickOnSeg(seg);
							sleep(2 * SECOND);
							
							continue;
						} else {
							// do the raiding!
							
							if (BHBot.scheduler.doRaidImmediately)
								BHBot.scheduler.doRaidImmediately = false; // reset it
							
							String raid = decideRaidRandomly();
							int difficulty = Integer.parseInt(raid.split(" ")[1]);
							raidType = Integer.parseInt(raid.split(" ")[0]);
							
							// ordered from most used to least.
							BHBot.log("Attempting " + (raidType == 3 ? "R3" : raidType == 4 ? "R4" : raidType == 1 ? "R1" : "R2") + " " + (difficulty == 3 ? "Heroic" : difficulty == 2 ? "Hard" : "Normal") + "...");
							
							int currentType = readCurrentRaidType();
							if (currentType == 0) { // an error!
								BHBot.log("Error: detected raid type is 0, which is an error. Restarting...");
								restart();
								continue;
							}
							
							if (currentType != raidType) {
								// we need to change the raid type!
								setRaidType(raidType, currentType);
								readScreen(2 * SECOND);
							}
							
							readScreen(2 * SECOND);
							seg = detectCue(cues.get("RaidSummon"));
							clickOnSeg(seg);
							sleep(2 * SECOND);
							readScreen();
							
							// dismiss character dialog if it pops up:
							readScreen();
							detectCharacterDialogAndHandleIt();
							
							seg = detectCue(cues.get(difficulty == 3 ? "Heroic" : difficulty == 2 ? "Hard" : "Normal"));
							clickOnSeg(seg);
							readScreen(2 * SECOND);
							seg = detectCue(cues.get("Accept"), 4 * SECOND);
							clickOnSeg(seg);
							
							if (handleTeamMalformedWarning()) {
								restart();
								continue;
							}
							
							state = State.Raid;
							BHBot.log("Raid initiated!");
							sleep(6 * SECOND);
						}
						
						continue;
					} // shards
					
					// check for tokens (trials and gauntlet):
					if ((BHBot.scheduler.doTrialsOrGauntletImmediately || ((BHBot.settings.doTrials || BHBot.settings.doGauntlet) && Misc.getTime() - timeLastTokensCheck > TOKENS_CHECK_INTERVAL)) && (STARTUP_BADGE_CHECK && STARTUP_TICKET_CHECK)) {
						if (!STARTUP_TOKEN_CHECK)
							STARTUP_TOKEN_CHECK = true;
						
						timeLastTokensCheck = Misc.getTime();
						seg = detectCue(cues.get("Trials"));
						if (seg == null) seg = detectCue(cues.get("Trials2"));
						boolean trials = seg != null; // if false, then we will do gauntlet instead of trials
						if (seg == null)
							seg = detectCue(cues.get("Gauntlet"));
						if (seg == null)
							seg = detectCue(cues.get("Gauntlet1"));
						if (seg == null) { // trials/gauntlet button not visible (perhaps it is disabled?)
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						
						clickOnSeg(seg);
						sleep(SECOND);
						
						// dismiss character dialog if it pops up:
						detectCharacterDialogAndHandleIt();
						
						readScreen();
						int tokens = getTokens();
						BHBot.log("Tokens: " + tokens);
						
						if (tokens == -1) { // error
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						
						if (!BHBot.scheduler.doTrialsOrGauntletImmediately && (tokens - BHBot.settings.costTokens < BHBot.settings.minTokens)) {
							seg = detectCue(cues.get("X"));
							clickOnSeg(seg);
							sleep(2 * SECOND);
							
							continue;
						} else {
							// do the trials/gauntlet!
							
							if (BHBot.scheduler.doTrialsOrGauntletImmediately)
								BHBot.scheduler.doTrialsOrGauntletImmediately = false; // reset it
							
							BHBot.log("Attempting " + (trials ? "Trials" : "Gauntlet") + " at difficulty level " + BHBot.settings.difficulty + "...");
							
							// select difficulty if needed:
							int difficulty = detectDifficulty();
							if (difficulty == 0) { // error!
								BHBot.log("Due to an error#1 in difficulty detection, " + (trials ? "trials" : "gauntlet") + " will be skipped.");
								closePopupSecurely(cues.get("TrialsOrGauntletWindow"), cues.get("X"));
								continue;
							}
							if (difficulty != BHBot.settings.difficulty) {
								BHBot.log("Current " + (trials ? "trials" : "gauntlet") + " difficulty level is " + difficulty + ", goal level is " + BHBot.settings.difficulty + ". Changing it...");
								boolean result = selectDifficulty(difficulty, BHBot.settings.difficulty);
								if (!result) { // error!
									// see if drop down menu is still open and close it:
									readScreen(SECOND);
									tryClosingWindow(cues.get("DifficultyDropDown"));
									readScreen(4 * SECOND);
									tryClosingWindow(cues.get("TrialsOrGauntletWindow"));
									BHBot.log("Due to an error#2 in difficulty selection, " + (trials ? "trials" : "gauntlet") + " will be skipped.");
									continue;
								}
							}
							
							// select cost if needed:
							readScreen(2 * SECOND); // wait for the popup to stabilize a bit
							int cost = detectCost();
							if (cost == 0) { // error!
								BHBot.log("Due to an error#1 in cost detection, " + (trials ? "trials" : "gauntlet") + " will be skipped.");
								closePopupSecurely(cues.get("TrialsOrGauntletWindow"), cues.get("X"));
								continue;
							}
							if (cost != (trials ? BHBot.settings.costTokens : BHBot.settings.costTokens)) {
								BHBot.log("Current " + (trials ? "trials" : "gauntlet") + " cost is " + cost + ", goal cost is " + (trials ? BHBot.settings.costTokens : BHBot.settings.costTokens) + ". Changing it...");
								boolean result = selectCost(cost, (trials ? BHBot.settings.costTokens : BHBot.settings.costTokens));
								if (!result) { // error!
									// see if drop down menu is still open and close it:
									readScreen(SECOND);
									tryClosingWindow(cues.get("CostDropDown"));
									readScreen(4 * SECOND);
									tryClosingWindow(cues.get("TrialsOrGauntletWindow"));
									BHBot.log("Due to an error#2 in cost selection, " + (trials ? "trials" : "gauntlet") + " will be skipped.");
									continue;
								}
							}
							
							seg = detectCue(cues.get("Play"), 2 * SECOND);
							if (seg == null) {
								BHBot.log("Error: Play button not found while trying to do " + (trials ? "trials" : "gauntlet") + ". Ignoring...");
								continue;
							}
							clickOnSeg(seg);
							readScreen(2 * SECOND);
							
							// dismiss character dialog if it pops up:
							detectCharacterDialogAndHandleIt();
							
							seg = detectCue(cues.get("Accept"), 4 * SECOND);
							clickOnSeg(seg);
							
							state = trials ? State.Trials : State.Gauntlet;
							
							BHBot.log((trials ? "Trials" : "Gauntlet") + " initiated!");
							sleep(6 * SECOND);
						}
						
						continue;
					} // tokens (trials and gauntlet)
					
					// check for energy:
					if ((BHBot.scheduler.doDungeonImmediately || (BHBot.settings.doDungeons && Misc.getTime() - timeLastEnergyCheck > ENERGY_CHECK_INTERVAL)) && (STARTUP_BADGE_CHECK && STARTUP_TICKET_CHECK)) {
						timeLastEnergyCheck = Misc.getTime();
						int energy = getEnergy();
						BHBot.log("Energy (at least): " + energy);
						
						if (energy == -1) { // error
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						
						if (!BHBot.scheduler.doDungeonImmediately && (energy - 20 < BHBot.settings.minEnergy)) {
							continue;
						} else {
							// do the dungeon!
							
							if (BHBot.scheduler.doDungeonImmediately)
								BHBot.scheduler.doDungeonImmediately = false; // reset it
							
							String dungeon = decideDungeonRandomly();
							int difficulty = Integer.parseInt(dungeon.split(" ")[1]);
							dungeon = dungeon.split(" ")[0];
							
							// check if we want to do world boss and do it.
							if (dungeon.charAt(3) == '5') {
								BHBot.log("Attempting World Boss...");
								boolean init = false;
								
								seg = detectCue(cues.get("WBButton"));
								clickOnSeg(seg);
								// need 5 sec so refresh button is not transparent
								sleep(5 * SECOND);
								
								// Try joining for 1 min
								BHBot.log("Will create a room if no team is found within 1 minute(s).");
								long startTime = Misc.getTime();
								while (Misc.getTime() - startTime < 1 * MINUTE && !init) {
									
									readScreen();
									seg = detectCue(cues.get("Join"));
									if (seg != null) {
										clickOnSeg(seg);
										sleep(3 * SECOND);
										
										readScreen();
										seg = detectCue(cues.get("Close"));
										if (seg != null) {
											sleep(2 * SECOND);
											clickOnSeg(seg);
										} else {
											readScreen();
											seg = detectCue(cues.get("Ready"));
											if (seg != null) {
												clickOnSeg(seg);
												sleep(2 * SECOND);
											}
										}
									} else {
										readScreen();
										seg = detectCue(cues.get("Close"));
										if (seg != null)
											clickOnSeg(seg);
										
										readScreen();
										seg = detectCue(cues.get("AutoOn"));
										if (seg != null) {
											init = true;
											break;
										} else {
											readScreen();
											seg = detectCue(cues.get("Refresh"));
											if (seg != null)
												clickOnSeg(seg);
											else
												sleep(5 * SECOND);
										}
									}
								}
								
								sleep(3 * SECOND);
								readScreen();
								seg = detectCue(cues.get("Netherworld"));
								while (seg != null) {
									sleep(6 * SECOND);
									readScreen();
									seg = detectCue(cues.get("Netherworld"));
								}
								
								if (!init) {
									sleep(3 * SECOND);
									readScreen();
									seg = detectCue(cues.get("AutoOn"));
									if (seg != null)
										init = true;
								}
								
								// make my own room
								if (!init) {
									seg = detectCue(cues.get("WBSummonButton"), 3 * SECOND);
									clickOnSeg(seg);
									
									seg = detectCue(cues.get("WBSummonButtonGreen0"), 3 * SECOND);
									clickOnSeg(seg);
									
									seg = detectCue(cues.get("WBSummonButtonGreen1"), 3 * SECOND);
									clickOnSeg(seg);
									
									seg = detectCue(cues.get("WBDown"), 3 * SECOND);
									clickOnSeg(seg);
									sleep(8 * SECOND);
									
									// need to check if everyone is ready + all spots are full before starting.
									while (!init) {
										sleep(6 * SECOND);
										readScreen();
										seg = detectCue(cues.get("WB1"));
										if (seg == null) {
											readScreen();
											seg = detectCue(cues.get("WB3"));
											if (seg == null) {
												readScreen();
												seg = detectCue(cues.get("WBStart"));
												if (seg != null) {
													clickOnSeg(seg);
													sleep(4 * SECOND);
													
													readScreen();
													seg = detectCue(cues.get("No"));
													if (seg != null) {
														clickOnSeg(seg);
													} else {
														sleep(2 * SECOND);
														seg = detectCue(cues.get("AutoOn"));
														if (seg != null) {
															init = true;
															BHBot.log("World Boss initiated!");
														}
													}
												}
											}
										}
									}
								}
								
								state = State.WorldBoss;
								sleep(6 * SECOND);
							} else {
								BHBot.log("Attempting Z" + dungeon.charAt(1) + "D" + dungeon.charAt(3) + "...");
								seg = detectCue(cues.get("Quest"));
								clickOnSeg(seg);
								readScreen(3 * SECOND);
								
								int goalZone = Integer.parseInt("" + dungeon.charAt(1));
								int currentZone = readCurrentZone();
								
								int vec = goalZone - currentZone; // movement vector
								while (vec != 0) { // move to the correct zone
									if (vec > 0) {
										// note that moving to the right will fail in case player has not unlocked the zone yet!
										seg = detectCue(cues.get("RightArrow"));
										if (seg == null)
											break; // happens for example when player hasn't unlock the zone yet
										clickOnSeg(seg);
										readScreen(3 * SECOND);
										vec--;
									} else {
										seg = detectCue(cues.get("LeftArrow"));
										clickOnSeg(seg);
										readScreen(3 * SECOND);
										vec++;
									}
								}
								
								currentZone = readCurrentZone();
								if (currentZone != goalZone) {
									BHBot.log("Zone change failed. Current zone is " + currentZone + ", goal zone is " + goalZone + ". Ignoring...");
									continue;
								}
								
								// click on the dungeon:
								Point p = getDungeonIconPos(dungeon);
								clickInGame(p.x, p.y);
								
								readScreen(3 * SECOND);
								// select difficulty (except when d4 is in play, then there is no difficulty to select!):
								if (dungeon.charAt(3) == '4') { // d4
									seg = detectCue(cues.get("Enter"));
									clickOnSeg(seg);
								} else { // d1-d3
									seg = detectCue(cues.get(difficulty == 3 ? "Heroic" : difficulty == 2 ? "Hard" : "Normal"));
									clickOnSeg(seg);
								}
								
								readScreen(4 * SECOND);
								seg = detectCue(cues.get("Accept"));
								clickOnSeg(seg);
								
								state = State.Dungeon;
								
								BHBot.log("Dungeon <" + dungeon + "> initiated!");
								sleep(6 * SECOND);
							}
							
							continue;
						}
					} // energy
					
					// check for tickets (PvP):
					if ((BHBot.scheduler.doPVPImmediately || (BHBot.settings.doPVP && Misc.getTime() - timeLastTicketsCheck > TICKETS_CHECK_INTERVAL)) && STARTUP_BADGE_CHECK) {
						if (!STARTUP_TICKET_CHECK)
							STARTUP_TICKET_CHECK = true;
						
						timeLastTicketsCheck = Misc.getTime();
						int tickets = getTickets();
						BHBot.log("Tickets: " + tickets);
						
						if (tickets == -1) { // error
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						
						if (!BHBot.scheduler.doPVPImmediately && (tickets - BHBot.settings.costTickets < BHBot.settings.minTickets)) {
							continue;
						} else {
							// do the pvp!
							
							if (BHBot.scheduler.doPVPImmediately)
								BHBot.scheduler.doPVPImmediately = false; // reset it
							
							BHBot.log("Attempting PvP...");
							stripDown();
							
							seg = detectCue(cues.get("PVP"));
							if (seg == null) {
								BHBot.log("PVP button not found. Skipping PVP...");
								dressUp();
								continue; // should not happen though
							}
							clickOnSeg(seg);
							
							// select cost if needed:
							readScreen(2 * SECOND); // wait for the popup to stabilize a bit
							int cost = detectCost();
							if (cost == 0) { // error!
								BHBot.log("Due to an error#1 in cost detection, PVP will be skipped.");
								closePopupSecurely(cues.get("PVPWindow"), cues.get("X"));
								dressUp();
								continue;
							}
							if (cost != BHBot.settings.costTickets) {
								BHBot.log("Current PVP cost is " + cost + ", goal cost is " + BHBot.settings.costTickets + ". Changing it...");
								boolean result = selectCost(cost, BHBot.settings.costTickets);
								if (!result) { // error!
									// see if drop down menu is still open and close it:
									readScreen(SECOND);
									tryClosingWindow(cues.get("CostDropDown"));
									readScreen(3 * SECOND);
									seg = detectCue(cues.get("PVPWindow"), 7 * SECOND);
									if (seg != null)
										closePopupSecurely(cues.get("PVPWindow"), cues.get("X"));
									BHBot.log("Due to an error#2 in cost selection, PVP will be skipped.");
									dressUp();
									continue;
								}
							}
							
							readScreen(3 * SECOND);
							seg = detectCue(cues.get("Play"));
							clickOnSeg(seg);
							
							// dismiss character dialog if it pops up:
							detectCharacterDialogAndHandleIt();
							
							seg = detectCue(cues.get("Fight"), 7 * SECOND); // it will select the first fight button, top-most (so the easiest enemy)
							clickOnSeg(seg);
							readScreen(4 * SECOND);
							
							seg = detectCue(cues.get("Accept"));
							clickOnSeg(seg);
							sleep(SECOND);
							
							state = State.PVP;
							
							BHBot.log("PVP initiated!");
							sleep(6 * SECOND);
						}
						
						continue;
					} // PvP
					
					// check for badges (for GVG/Invasion):
					if (BHBot.scheduler.doGVGImmediately || BHBot.scheduler.doInvasionImmediately || ((BHBot.settings.doGVG || BHBot.settings.doInvasion) && Misc.getTime() - timeLastBadgesCheck > BADGES_CHECK_INTERVAL)) {
						if (!STARTUP_BADGE_CHECK)
							STARTUP_BADGE_CHECK = true;
						
						timeLastBadgesCheck = Misc.getTime();
						
						BadgeEvent badgeEvent = BadgeEvent.None;
						
						seg = detectCue(cues.get("GVG"));
						if (seg != null) {
							badgeEvent = BadgeEvent.GVG;
						} else {
							seg = detectCue(cues.get("Invasion"));
							if (seg != null)
								badgeEvent = BadgeEvent.Invasion;
						}
						
						if (badgeEvent == BadgeEvent.None) { // GvG/invasion button not visible (perhaps this week there is no GvG/Invasion event?)
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						
						clickOnSeg(seg);
						sleep(SECOND);
						
						detectCharacterDialogAndHandleIt(); // needed for invasion
						
						readScreen();
						int badges = getBadges();
						BHBot.log("Badges: " + badges);
						
						if (badges == -1) { // error
							BHBot.scheduler.restoreIdleTime();
							continue;
						}
						
						// check GVG:
						if (badgeEvent == BadgeEvent.GVG) {
							if (!BHBot.scheduler.doGVGImmediately && (badges - BHBot.settings.costGVG < BHBot.settings.minBadgesGVG)) {
								seg = detectCue(cues.get("X"));
								clickOnSeg(seg);
								sleep(2 * SECOND);
								
								continue;
							} else {
								// do the GVG!
								
								if (BHBot.scheduler.doGVGImmediately)
									BHBot.scheduler.doGVGImmediately = false; // reset it
								
								BHBot.log("Attempting GvG...");
								// remove gear for GvG wins
								seg = detectCue(cues.get("X"));
								clickOnSeg(seg);
								readScreen(SECOND);
								stripDown();
								readScreen(3 * SECOND);
								seg = detectCue(cues.get("GVG"));
								clickOnSeg(seg);
								
								// select cost if needed:
								readScreen(2 * SECOND); // wait for the popup to stabilize a bit
								int cost = detectCost();
								if (cost == 0) { // error!
									BHBot.log("Due to an error#1 in cost detection, GVG will be skipped.");
									closePopupSecurely(cues.get("GVGWindow"), cues.get("X"));
									dressUp();
									continue;
								}
								if (cost != BHBot.settings.costGVG) {
									BHBot.log("Current GVG cost is " + cost + ", goal cost is " + BHBot.settings.costGVG + ". Changing it...");
									boolean result = selectCost(cost, BHBot.settings.costGVG);
									if (!result) { // error!
										// see if drop down menu is still open and close it:
										readScreen(SECOND);
										tryClosingWindow(cues.get("CostDropDown"));
										seg = detectCue(cues.get("GVGWindow"), 7 * SECOND);
										if (seg != null)
											readScreen(4 * SECOND);
										result = closePopupSecurely(cues.get("GVGWindow"), cues.get("X"));
										
										BHBot.log("Due to an error#2 in cost selection, GVG will be skipped.");
										seg = detectCue(cues.get("X"), 7 * SECOND);
										if (seg != null) clickOnSeg(seg);
										dressUp();
										continue;
									}
								}
								
								readScreen(3 * SECOND);
								seg = detectCue(cues.get("Play"));
								clickOnSeg(seg);
								
								readScreen(3 * SECOND);
								seg = detectCue(cues.get("Fight"));
								clickOnSeg(seg);
								
								readScreen(4 * SECOND);
								seg = detectCue(cues.get("Accept"));
								clickOnSeg(seg);
								sleep(SECOND);
								
								if (handleTeamMalformedWarning()) {
									restart();
									continue;
								}
								
								state = State.GVG;
								
								BHBot.log("GVG initiated!");
								sleep(6 * SECOND);
							}
							
							continue;
						} // GvG
						// check invasion:
						else if (true) {
							if (!BHBot.scheduler.doInvasionImmediately && (badges - BHBot.settings.costBadges < BHBot.settings.minBadges)) {
								seg = detectCue(cues.get("X"));
								clickOnSeg(seg);
								sleep(2 * SECOND);
								
								continue;
							} else {
								// do the invasion!
								
								if (BHBot.scheduler.doInvasionImmediately)
									BHBot.scheduler.doInvasionImmediately = false; // reset it
								
								BHBot.log("Attempting Invasion...");
								
								// select cost if needed:
								readScreen(2 * SECOND); // wait for the popup to stabilize a bit
								int cost = detectCost();
								if (cost == 0) { // error!
									BHBot.log("Due to an error#1 in cost detection, invasion will be skipped.");
									closePopupSecurely(cues.get("InvasionWindow"), cues.get("X"));
									continue;
								}
								if (cost != BHBot.settings.costBadges) {
									BHBot.log("Current invasion cost is " + cost + ", goal cost is " + BHBot.settings.costBadges + ". Changing it...");
									boolean result = selectCost(cost, BHBot.settings.costBadges);
									if (!result) { // error!
										// see if drop down menu is still open and close it:
										readScreen(SECOND);
										tryClosingWindow(cues.get("CostDropDown"));
										seg = detectCue(cues.get("InvasionWindow"), 7 * SECOND);
										if (seg != null)
											readScreen(4 * SECOND);
										result = closePopupSecurely(cues.get("InvasionWindow"), cues.get("X"));
										BHBot.log("Due to an error#2 in cost selection, invasion will be skipped.");
										continue;
									}
								}
								
								readScreen(3 * SECOND);
								seg = detectCue(cues.get("Play"));
								clickOnSeg(seg);
								
								readScreen(4 * SECOND);
								seg = detectCue(cues.get("Accept"));
								clickOnSeg(seg);
								sleep(SECOND);
								
								if (handleTeamMalformedWarning()) {
									restart();
									continue;
								}
								
								state = State.Invasion;
								
								BHBot.log("Invasion initiated!");
								sleep(6 * SECOND);
							}
							
							continue;
						} // invasion
						else {
							// do neither gvg nor invasion
							seg = detectCue(cues.get("X"));
							clickOnSeg(seg);
							sleep(2 * SECOND);
							continue;
						}
					} // badges
				} // main screen processing
			} catch (Exception e) {
				if (e instanceof org.openqa.selenium.WebDriverException && e.getMessage().startsWith("chrome not reachable")) {
					// this happens when user manually closes the Chrome window, for example
					e.printStackTrace();
					BHBot.log("Error: chrome is not reachable! Restarting...");
					restart();
					continue;
				} else if (e instanceof java.awt.image.RasterFormatException) {
					// not sure in what cases this happen, but it happens
					e.printStackTrace();
					BHBot.log("Error: RasterFormatException. Attempting to re-align the window...");
					sleep(500);
					scrollGameIntoView();
					sleep(500);
					try {
						readScreen();
					} catch (Exception e2) {
						BHBot.log("Error: re-alignment failed(" + e2.getMessage() + "). Restarting...");
						restart();
						continue;
					}
					BHBot.log("Realignment seems to have worked.");
					continue;
				} else if (e instanceof org.openqa.selenium.StaleElementReferenceException) {
					// this is a rare error, however it happens. See this for more info:
					// http://www.seleniumhq.org/exceptions/stale_element_reference.jsp
					e.printStackTrace();
					BHBot.log("Error: StaleElementReferenceException. Restarting...");
					restart();
					continue;
				} else if (e instanceof com.assertthat.selenium_shutterbug.utils.web.ElementOutsideViewportException) {
					BHBot.log("Error: ElementOutsideViewportException. Ignoring...");
					// we must not call 'continue' here, because this error could be a loop error, this is why we need to increase numConsecutiveException bellow in the code!
				} else {
					// unknown error!
					e.printStackTrace();
				}
				
				numConsecutiveException++;
				if (numConsecutiveException > MAX_CONSECUTIVE_EXCEPTIONS) {
					numConsecutiveException = 0; // reset it
					BHBot.log("Problem detected: number of consecutive exceptions is higher than " + MAX_CONSECUTIVE_EXCEPTIONS + ". This probably means we're caught in a loop. Restarting...");
					restart();
					continue;
				}
				
				BHBot.scheduler.restoreIdleTime();
				
				continue;
			}
			
			// well, we got through all the checks. Means that nothing much has happened. So lets sleep for a few seconds in order to not make processing too heavy...
			numConsecutiveException = 0; // reset exception counter
			BHBot.scheduler.restoreIdleTime(); // revert changes to idle time
			
			if (BHBot.settings.resetTimersOnBattleEnd && Misc.getTime() - BHBot.scheduler.getIdleTime() > 2 * MINUTE)
				BHBot.processCommand("exit");
			
			if (finished) break; // skip sleeping if finished flag has been set!
			
			sleep(6 * SECOND);
			
			if (Misc.getTime() - timeLastSettingsCheck > MAX_SETTINGS_RELOAD_TIME) {
				BHBot.processCommand("reload");
				timeLastSettingsCheck = Misc.getTime();
			}
		} // main while loop
		
		BHBot.log("Stopping main thread...");
		//driver.close();
		driver.quit();
		BHBot.log("Main thread stopped.");
	}
	
	/**
	 * This form opens only seldom (haven't figured out what triggers it exactly - perhaps some cookie expired?). We need to handle it!
	 */
	private void detectSignInFormAndHandleIt() {
		// close the popup "create new account" form (that hides background):
		WebElement btnClose;
		try {
			btnClose = driver.findElement(By.cssSelector("#kongregate_lightbox_wrapper > div.header_bar > a"));
		} catch (NoSuchElementException e) {e.printStackTrace();
			return;
		}
		btnClose.click();
		
		// fill in username and password:
		WebElement weUsername;
		try {
			weUsername = driver.findElement(By.xpath("//*[@id='username']"));
		} catch (NoSuchElementException e) {e.printStackTrace();
			return;
		}
		weUsername.clear();
		weUsername.sendKeys(BHBot.settings.username);
		
		WebElement wePassword;
		try {
			wePassword = driver.findElement(By.xpath("//*[@id='password']"));
		} catch (NoSuchElementException e) {e.printStackTrace();
			return;
		}
		wePassword.clear();
		wePassword.sendKeys(BHBot.settings.password);
		
		// press the "sign-in" button: 
		WebElement btnSignIn;
		try {
			btnSignIn = driver.findElement(By.id("sessions_new_form_spinner"));
		} catch (NoSuchElementException e) {e.printStackTrace();
			return;
		}
		btnSignIn.click();
		
		BHBot.log("Signed-in manually (sign-in prompt was open).");
	}
	
	/**
	 * Handles login screen (it shows seldom though. Perhaps because some cookie expired or something... anyway, we must handle it or else bot can't play the game anymore).
	 */
	private void detectLoginFormAndHandleIt() {
		readScreen();
		
		MarvinSegment seg = detectCue(cues.get("Login"));
		
		if (seg == null)
			return;
		
		// open login popup window:
		getJS().executeScript("active_user.activateInlineLogin(); return false;"); // I found this code within page source itself (it gets triggered upon clicking on some button)
		
		sleep(5000); // if we don't sleep enough, login form may still be loading and code bellow will not get executed!
		
		// fill in username:
		WebElement weUsername;
		try {
			weUsername = driver.findElement(By.cssSelector("body#play > div#lightbox > div#lbContent > div#kongregate_lightbox_wrapper > div#lightbox_form > div#lightboxlogin > div#new_session_shared_form > form > dl > dd > input#username"));
		} catch (NoSuchElementException e) {
			BHBot.log("Problem: username field not found in the login form (perhaps it was not loaded yet?)!");
			return;
		}
		weUsername.clear();
		weUsername.sendKeys(BHBot.settings.username);
		BHBot.log("Username entered into the login form.");
		
		WebElement wePassword;
		try {
			wePassword = driver.findElement(By.cssSelector("body#play > div#lightbox > div#lbContent > div#kongregate_lightbox_wrapper > div#lightbox_form > div#lightboxlogin > div#new_session_shared_form > form > dl > dd > input#password"));
		} catch (NoSuchElementException e) {
			BHBot.log("Problem: password field not found in the login form (perhaps it was not loaded yet?)!");
			return;
		}
		wePassword.clear();
		wePassword.sendKeys(BHBot.settings.password);
		BHBot.log("Password entered into the login form.");
		
		// press the "sign-in" button: 
		WebElement btnSignIn;
		try {
			btnSignIn = driver.findElement(By.cssSelector("body#play > div#lightbox > div#lbContent > div#kongregate_lightbox_wrapper > div#lightbox_form > div#lightboxlogin > div#new_session_shared_form > form > dl > dt#signin > input"));
		} catch (NoSuchElementException e) {e.printStackTrace();
			return;
		}
		btnSignIn.click();
		
		BHBot.log("Signed-in manually (we were signed-out).");
		
		scrollGameIntoView();
	}
	
	/**
	 * This will handle dialog that open up when you encounter a boss for the first time, for example, or open a raid window or trials window for the first time, etc.
	 */
	private void detectCharacterDialogAndHandleIt() {
		final Color cuec1 = new Color(238, 241, 249); // white
		final Color cuec2 = new Color(82, 90, 98); // gray
		
		MarvinSegment right;
		MarvinSegment left;
		int steps = 0;
		
		while (true) {
			readScreen();
			
			right = detectCue(cues.get("DialogRight"));
			left = detectCue(cues.get("DialogLeft"));
			
			// double check right-side dialog cue:
			if (right != null) {
				if (
						!(new Color(img.getRGB(right.x2 + 1, right.y1 + 24))).equals(cuec1) ||
								!(new Color(img.getRGB(right.x2 + 4, right.y1 + 24))).equals(cuec2)
						)
					right = null;
			}
			
			// double check left-side dialog cue:
			if (left != null) {
				if (
						!(new Color(img.getRGB(left.x1 - 1, left.y1 + 24))).equals(cuec1) ||
								!(new Color(img.getRGB(left.x1 - 4, left.y1 + 24))).equals(cuec2)
						)
					left = null;
			}
			
			if (left == null && right == null)
				break; // dialog not detected
			
			// click to dismiss/progress it:
			if (left != null)
				clickOnSeg(left);
			else
				clickOnSeg(right);
			
			sleep(2 * SECOND);
			steps++;
		}
		
		if (steps > 0)
			BHBot.log("Character dialog dismissed.");
	}
	
	private BufferedImage takeScreenshot() {
		return takeScreenshot(true);
	}
	
	private BufferedImage takeScreenshot(boolean ofGame) {
		if (ofGame)
			return Shutterbug.shootElement(driver, game).getImage();
		else
			return Shutterbug.shootPage(driver).getImage();
	}
	
	public void readScreen() {
		readScreen(true);
	}
	
	/**
	 * @param game if true, then screenshot of a WebElement will be taken that contains the flash game. If false, then simply a screenshot of a browser will be taken.
	 */
	private void readScreen(boolean game) {
		readScreen(0, game);
	}
	
	/**
	 * First sleeps 'wait' milliseconds and then reads the screen. It's a handy utility method that does two things in one command.
	 */
	private void readScreen(int wait) {
		readScreen(wait, true);
	}
	
	/**
	 * @param wait first sleeps 'wait' milliseconds and then reads the screen. It's a handy utility method that does two things in one command.
	 * @param game if true, then screenshot of a WebElement will be taken that contains the flash game. If false, then simply a screenshot of a browser will be taken.
	 */
	private void readScreen(int wait, boolean game) {
		if (wait != 0)
			sleep(wait);
		img = takeScreenshot(game);
		
		// detect and handle "Loading" message (this is optional operation though):
		Cue cue = cues.get("Loading");
		int counter = 0;
		while (true) {
			List<MarvinSegment> result = FindSubimage.findSubimage(
					img,
					cue.im,
					1.0,
					false,
					true, // treat transparent pixels as obscured background
					cue.bounds != null ? cue.bounds.x1 : 0,
					cue.bounds != null ? cue.bounds.y1 : 0,
					cue.bounds != null ? cue.bounds.x2 : 0,
					cue.bounds != null ? cue.bounds.y2 : 0
			);
			
			if (result.size() == 0)
				break; // we're clear of "Loading" message
			
			sleep(3 * SECOND); // wait a bit for the "Loading" to go away
			img = takeScreenshot(game);
			counter++;
			if (counter > 20) {
				BHBot.log("Problem detected: loading screen detected, however timeout reached while waiting for it to go away. Ignoring...");
				break; // taking too long... will probably not load at all. We must restart it (we won't restart it from here, but idle detection mechanism will)
			}
		}
	}
	
	// https://stackoverflow.com/questions/297762/find-known-sub-image-in-larger-image
	public static MarvinSegment findSubimage(BufferedImage src, Cue cue) {
		long timer = Misc.getTime();
		
		MarvinSegment seg;
		
		seg = FindSubimage.findImage(
				src,
				cue.im,
				cue.bounds != null ? cue.bounds.x1 : 0,
				cue.bounds != null ? cue.bounds.y1 : 0,
				cue.bounds != null ? cue.bounds.x2 : 0,
				cue.bounds != null ? cue.bounds.y2 : 0
		);
		
		//source.drawRect(seg.x1, seg.y1, seg.x2-seg.x1, seg.y2-seg.y1, Color.blue);
		//MarvinImageIO.saveImage(source, "window_out.png");
		if (BHBot.settings.debugDetectionTimes)
			BHBot.log("cue detection time: " + (Misc.getTime() - timer) + "ms (" + cue.name + ") [" + (seg != null ? "true" : "false") + "]");
		return seg;
	}
	
	public MarvinSegment detectCue(Cue cue) {
		return findSubimage(img, cue);
	}
	
	public MarvinSegment detectCue(Cue cue, int timeout, Bounds bounds) {
		return detectCue(new Cue(cue, bounds), timeout, true);
	}
	
	public MarvinSegment detectCue(Cue cue, Bounds bounds) {
		return detectCue(new Cue(cue, bounds), 0, true);
	}
	
	public MarvinSegment detectCue(Cue cue, int timeout) {
		return detectCue(cue, timeout, true);
	}
	
	/**
	 * Will try (and retry) to detect cue from image until timeout is reached. May return null if cue has not been detected within given 'timeout' time. If 'timeout' is 0,
	 * then it will attempt at cue detection only once and return the result immediately.
	 */
	public MarvinSegment detectCue(Cue cue, int timeout, boolean game) {
		long timer = Misc.getTime();
		MarvinSegment seg = findSubimage(img, cue);
		
		while (seg == null) {
			if (Misc.getTime() - timer >= timeout)
				break;
			sleep(500);
			readScreen(game);
			seg = findSubimage(img, cue);
		}
		
		if (seg == null && timeout > 0) { // segment not detected when expected (timeout>0 tells us that we probably expect to find certain cue, since we are waiting for it to appear)
			if (handlePM()) { // perhaps PM window has opened and that is why we couldn't detect the cue?
				sleep(3 * SECOND);
				readScreen(game);
				seg = findSubimage(img, cue); // re-read the original segment
			}
		}
		
		return seg;
	}
	
	private int getSegCenterX(MarvinSegment seg) {
		return (seg.x1 + seg.x2) / 2;
	}
	
	private int getSegCenterY(MarvinSegment seg) {
		return (seg.y1 + seg.y2) / 2;
	}
	
	/**
	 * Moves mouse to position (0,0) in the 'game' element (so that it doesn't trigger any highlight popups or similar
	 */
	private void moveMouseAway() {
		try {
			Actions act = new Actions(driver);
			act.moveToElement(game, 0, 0);
			act.perform();
		} catch (Exception e) {e.printStackTrace();
			// do nothing
		}
	}
	
	/**
	 * Performs a mouse click on the center of the given segment
	 */
	private void clickOnSeg(MarvinSegment seg) {
		Actions act = new Actions(driver);
		act.moveToElement(game, getSegCenterX(seg), getSegCenterY(seg));
		act.click();
		act.moveToElement(game, 0, 0); // so that the mouse doesn't stay on the button, for example. Or else button will be highlighted and cue won't get detected!
		act.perform();
	}
	
	// dont think this is working yet?
	private void clickAndHoldOnSeg(MarvinSegment seg, int secondsHolding) {
		Actions act = new Actions(driver);
		act.moveToElement(game, getSegCenterX(seg), getSegCenterY(seg));
		act.clickAndHold();
		act.moveToElement(game, 0, 0); // so that the mouse doesn't stay on the button, for example. Or else button will be highlighted and cue won't get detected!
		act.perform();
		
		sleep(secondsHolding * SECOND);
		
		act.release();
		act.perform();
	}
	
	private void clickInGame(int x, int y) {
		Actions act = new Actions(driver);
		act.moveToElement(game, x, y);
		act.click();
		act.moveToElement(game, 0, 0); // so that the mouse doesn't stay on the button, for example. Or else button will be highlighted and cue won't get detected!
		act.perform();
	}
	
	public void sleep(int milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns amount of "full" pixels in x direction from specified pixel. Used in getEnergy and other resource getters.
	 * first should have the possibility to be "full".
	 * This uses the best possible interval, so we have to check the least amount of pixels, hence sqrt use.
	 * Take the derivative of (pixels/x)+x and set it to 0.
	 */
	private int getPixelsFullX(int firstPixelX, int pixelsY, int pixels, Color color) {
		// flooring this because we rarely check the whole bar.
		int intervalOptimal = (int) Math.floor(Math.sqrt(pixels + 1));
		// make intervals / pixels to check first. needs to be rounded up to not exclude some pixels
		int steps = (int) Math.ceil((double) pixels / intervalOptimal);
		
		int pixelsFull = 0;
		
		// first full pixel is always checked, if it's the wrong color we return 0
		Color col = new Color(img.getRGB(firstPixelX, pixelsY));
		if (!col.equals(color))
			return 0;
		else {
			for (int i = 1; i <= steps; i++) {
				col = new Color(img.getRGB(firstPixelX + (i * intervalOptimal), pixelsY));
				
				if (!col.equals(color)) {
					// check pixels backwards. current pixel is empty.
					int pixelsEmpty = 1;
					
					col = new Color(img.getRGB(firstPixelX + ((i * intervalOptimal) - pixelsEmpty), pixelsY));
					// check the pixels we might jump over.
					// can be j < interval. but fuck it, lets just be safe and check 1 extra. too little drops pixels, too much doesn't do much
					for (int j = 0; j <= intervalOptimal; j++) {
						if (col.equals(color))
							break;
						
						pixelsEmpty++;
						
						col = new Color(img.getRGB(firstPixelX + ((i * intervalOptimal) - pixelsEmpty), pixelsY));
					}
					
					pixelsFull = ((i * intervalOptimal) + 1) - pixelsEmpty; // + 1 for the first pixel
					
					break;
				}
			}
		}
		
		return pixelsFull;
	}
	
	/**
	 * Returns amount of energy in percent (0-100). Returns -1 in case it cannot read energy for some reason.
	 */
	private int getEnergy() {
		MarvinSegment seg;
		seg = detectCue(cues.get("EnergyBar"));
		
		if (seg == null) {
			BHBot.log("Can't detect token bar.");
			return -1;
		}
		
		int left = seg.x2 + 1;
		int top = seg.y1 + 6;
		final Color full = new Color(136, 197, 44);
		//final Color limit = new Color(87, 133, 21);
		//final Color empty = new Color(49, 50, 51);
		
		// Starts at 2 to account for the 2 pixels we don't check for. they're always full.
		int pixelsFull = 2;
		
		// left is the first pixel in the "full" bar
		// energy bar is 80 pixels long (however last two pixels will have "medium" color and not full color (it's so due to shading). check 78 pixels
		pixelsFull += getPixelsFullX(left, top, 78, full);
		
		return Math.floorDiv((pixelsFull * BHBot.settings.maxEnergy), 80);
	}
	
	/**
	 * Returns number of tickets left (for PvP) in interval [0..10]. Returns -1 in case it cannot read number of tickets for some reason.
	 */
	private int getTickets() {
		MarvinSegment seg;
		
		seg = detectCue(cues.get("TicketBar"));
		
		if (seg == null) {
			BHBot.log("Can't detect token bar.");
			return -1;
		}
		
		int left = seg.x2 + 1;
		int top = seg.y1 + 6;
		
		final Color full = new Color(226, 42, 81);
		
		// Starts at 2 to account for the 2 pixels we don't check for. they're always full.
		int pixelsFull = 2;
		
		// ticket bar is 80 pixels long (however last two pixels will have "medium" color and not full color (it's so due to shading))
		pixelsFull += getPixelsFullX(left, top, 78, full);
		
		return Math.round((pixelsFull * BHBot.settings.maxTickets) / 80f); // scale it to interval [0..10]
	}
	
	/**
	 * Returns number of shards that we have. Works only if raid popup is open. Returns -1 in case it cannot read number of shards for some reason.
	 */
	private int getShards() {
		MarvinSegment seg;
		
		seg = detectCue(cues.get("RaidPopup"));
		
		if (seg == null) // this should probably not happen
			return -1;
		
		int left = seg.x2 + 1;
		int top = seg.y1 + 9;
		
		final Color full = new Color(199, 79, 175);
		
		// start with 2 that we don't check (always full)
		int pixelsFull = 2;
		
		// shards bar is 80 pixels wide. So we check 78 pixels in x direction.
		pixelsFull += getPixelsFullX(left, top, 78, full);
		
		return Math.round((pixelsFull * BHBot.settings.maxShards) / 80f); // scale it to interval [0..x]
	}
	
	/**
	 * Returns number of tokens we have. Works only if trials/gauntlet window is open. Returns -1 in case it cannot read number of tokens for some reason.
	 */
	private int getTokens() {
		MarvinSegment seg;
		
		seg = detectCue(cues.get("TokenBar"));
		
		// this should probably not happen
		if (seg == null) {
			BHBot.log("Can't detect token bar.");
			return -1;
		}
		
		int left = seg.x2 + 1;
		int top = seg.y1 + 6;
		
		final Color full = new Color(17, 208, 226);
		
		int pixelsFull = 2;
		
		// tokens bar is 78 pixels wide (however last two pixels will have "medium" color and not full color (it's so due to shading))
		pixelsFull += getPixelsFullX(left, top, 76, full);
		
		return Math.round((pixelsFull * BHBot.settings.maxTokens) / 78f); // scale it to interval [0..10]
	}
	
	/**
	 * Returns number of badges we have. Works only if GVG window is open. Returns -1 in case it cannot read number of badges for some reason.
	 */
	private int getBadges() {
		MarvinSegment seg;
		
		seg = detectCue(cues.get("BadgeBar"));
		
		if (seg == null) {
			BHBot.log("Can't detect token bar.");
			return -1;
		}
		
		int left = seg.x2 + 1;
		int top = seg.y1 + 6;
		
		final Color full = new Color(17, 208, 226);
		
		// Starts at 2 to account for the 2 pixels we don't check for. they're always full.
		int pixelsFull = 2;
		
		// badges bar is 78 pixels wide (however last two pixels will have "medium" color and not full color (it's so due to shading))
		pixelsFull += getPixelsFullX(left, top, 76, full);
		
		return Math.round((pixelsFull * BHBot.settings.maxBadges) / 78f); // scale it to interval [0..10]
	}
	
	/**
	 * Ad window must be open in order for this to work. Either dungeon video ad popup or main screen video pop up (works with both,
	 * though they are different windows).
	 *
	 * @return true in case it successfully skipped an ad
	 */
	/*
	private boolean trySkippingAd() {
		MarvinSegment seg = null;
		
		int counter = 0;
		do {
			if (counter > 10)
				return false;
			
			readScreen(500);
			counter++;
			seg = detectCue(cues.get("Decline"));
			if (seg == null)
				seg = detectCue(cues.get("Skip"));
		} while (seg == null);
		
		clickOnSeg(seg);
		
		readScreen(SECOND);
		seg = detectCue(cues.get("YesGreen"), 4 * SECOND);
		clickOnSeg(seg);
		
		sleep(2 * SECOND); // wait a bit for the popup to disappear
		
		return true;
	}
	*/
	
	/**
	 * @param closeItemsPopup if true, then this method will attempt to close the items popup window at the end of the process of claiming ad.
	 *                        We get that window when in the main screen of the game
	 * @return false means that the caller must restart bot (due to an error).
	 */
	/*
	public ReturnResult waitForAdAndCloseIt(boolean closeItemsPopup) {
		boolean done = false;
		long timer = Misc.getTime();
		do {
			if (Misc.getTime() - timer > 60 * SECOND) break;
			
			sleep(500);
			try {
				// see if we're out of offers:
				try {
					boolean outOfOffers = driver.findElement(By.cssSelector("#epom-tag-container-overlay > div > span")).getText().equalsIgnoreCase(":("); // "We're out of offers to show in your region" message
					if (outOfOffers) {
						BHBot.log("Seems there are no ad offers available anymore in our region. Skipping ad offer...");
						
						// click the close button:
						WebElement btnClose;
						try {
							btnClose = driver.findElement(By.cssSelector("#play > div.ironrv-container.open > div.ironrv-container-header > div.ironrv-close"));
						} catch (NoSuchElementException e) {
							return ReturnResult.error("Cannot find the close button on 'out of offers' ad window!");
						}
						btnClose.click();
						
						sleep(2000); // wait for ad window to fade out (it slowly fades out)
						
						boolean skipped = trySkippingAd();
						
						BHBot.log("Ad " + (skipped ? "successfully" : "unsuccessfully") + " skipped! (location: " + state.getName() + ")");
						
						return ReturnResult.ok();
					}
				} catch (Exception e) {e.printStackTrace();
				}
				
				done = driver.findElement(By.cssSelector("#play > div.ironrv-container.open > div.ironrv-container-header.complete > div.ironrv-title > span")).getText().equalsIgnoreCase("You can now claim your reward!");
				if (done) {
					// click the close button:
					WebElement btnClose;
					try {
					
					//this under was commented out from before.
						/*
						 		Close button:
								//*[@id="play"]/div[14]/div[1]/div[3]
								#play > div.ironrv-container.open > div.ironrv-container-header.complete > div.ironrv-close
								<div class="ironrv-close " style="">X</div>
						 */
	/*
						btnClose = driver.findElement(By.cssSelector("#play > div.ironrv-container.open > div.ironrv-container-header.complete > div.ironrv-close"));
					} catch (NoSuchElementException e) {
						return ReturnResult.error("Cannot find the close button on finished ad window!");
					}
					btnClose.click();
					
					break;
				}
			} catch (Exception e) {e.printStackTrace();
			}
		} while (!done);
		
		if (!done) {
			return ReturnResult.error("Could not claim ad reward... timeout!");
		} else {
			if (closeItemsPopup) {
				sleep(4 * SECOND);
				scrollGameIntoView();
				readScreen();
				
				MarvinSegment seg = detectCue(cues.get("Items"), 4 * SECOND);
				if (seg == null) {
					return ReturnResult.error("There is no 'Items' button after watching the ad.");
				}
				
				seg = detectCue(cues.get("X"));
				clickOnSeg(seg);
				
				sleep(2 * SECOND);
			}
			BHBot.log("Ad reward successfully claimed! (location: " + state.getName() + ")");
			return ReturnResult.ok();
		}
	}
	*/
	
	/**
	 * Processes any kind of dungeon: <br>
	 * - normal dungeon <br>
	 * - raid <br>
	 * - trial <br>
	 * - gauntlet <br>
	 * - world boss <br>
	 */
	private void processDungeon() {
		MarvinSegment seg;
		
		// handle "Not enough energy" popup:
		boolean insufficientEnergy = handleNotEnoughEnergyPopup();
		if (insufficientEnergy) {
			state = State.Main; // reset state
			return;
		}
		
		// check for any character dialog:
		detectCharacterDialogAndHandleIt();
		
		// check for 1X and 3X speed (and increase it):
		/*
		int speed = 0; // unknown
		seg = detectCue(cues.get("1Xspeed"));
		if (seg != null)
			speed = 1;
		if (speed == 0) {
			seg = detectCue(cues.get("3Xspeed"));
			if (seg != null)
				speed = 3;
		}
		if (speed == 1 || speed == 3) {
			clickOnSeg(seg);
			if (speed == 1)
				clickOnSeg(seg); // click twice to increase speed to 5X	
			BHBot.log("Increased battle speed (old speed=" + speed + "X).");
			return;
		}
		*/

		seg = detectCue(cues.get("AutoOff"));
		if (seg != null) {
			clickOnSeg(seg);
			BHBot.log("Auto-pilot is disabled. Enabling...");
			
			return;
		}
		
		// check for ad treasure:
		/*
		seg = detectCue(cues.get("AdTreasure"));
		if (seg != null) {
			seg = detectCue(cues.get("Watch2"), 4 * SECOND);
			clickOnSeg(seg);
			
			sleep(40 * SECOND);
			
			ReturnResult result = waitForAdAndCloseIt(false);
			if (result.msg != null) {
				Misc.log("Error: " + result.msg);
			}
			
			sleep(4 * SECOND);
			
			// note that the reward window closes automatically inside dungeons (when autopilot is enabled)
			
			scrollGameIntoView();
			
			sleep(2 * SECOND);
			
			return;
		}
		*/
		
		
		// Check for persuasions
		seg = detectCue(cues.get("Persuade"));
		if (seg != null) {
			// If it's a raid familiar, we might want to bribe
			if (state == State.Raid) {
				seg = detectCue(cues.get("4000Persuade"));
				if (seg == null) {
					try {
						saveGameScreen("familiarLegendary");
					} catch (Exception e) {
						// ignore it
					}
					
					readScreen(2 * SECOND);
					
					BHBot.log("Legendary familiar detected in a raid! BRIBING.");
					seg = detectCue(cues.get("Bribe"));
					if (seg != null) {
						clickOnSeg(seg);
						sleep(SECOND);
						
						readScreen(2 * SECOND);
						seg = detectCue(cues.get("YesGreen"));
						if (seg != null) {
							clickOnSeg(seg);
						} else {
							BHBot.log("Can't detect YesGreen button for a bribe. Sleeping for 20 minutes.");
							sleep(30 * MINUTE);
						}
						
						BHBot.log("Bribed a raid legendary familiar!");
						sleep(6 * SECOND);
					} else {
						BHBot.log("Can't detect Bribe button for a bribe. Sleeping for 20 minutes.");
						sleep(30 * MINUTE);
					}
				} else {
					seg = detectCue(cues.get("Persuade"));
						clickOnSeg(seg);
						sleep(2 * SECOND);
						
						readScreen();
						seg = detectCue(cues.get("YesGreen"));
						clickOnSeg(seg);
						
						BHBot.log("Persuasion attempted in a raid.");
						sleep(6 * SECOND);
				}
			} else {
				clickOnSeg(seg);
				sleep(2 * SECOND);
				
				readScreen();
				seg = detectCue(cues.get("YesGreen"));
				clickOnSeg(seg);
				BHBot.log("Persuasion attempted.");
				sleep(6 * SECOND);
			}
			// return at the end, there is probably nothing in this method to check for a while now
			// lettuce return
			return;
		}
		
		// check for skeleton treasure chest (and decline it):
		seg = detectCue(cues.get("SkeletonTreasure"));
		if (seg != null) {
			if (raidType == 4 || raidType == 5) {
				readScreen(2 * SECOND);
				seg = detectCue(cues.get("SkeletonTreasureOpen"));
				clickOnSeg(seg);
				
				readScreen(2 * SECOND);
				seg = detectCue(cues.get("YesGreen"));
				clickOnSeg(seg);
				
				return;
			} else {
				seg = detectCue(cues.get("Decline"));
				clickOnSeg(seg);
				
				readScreen(2 * SECOND);
				seg = detectCue(cues.get("YesGreen"));
				clickOnSeg(seg);
				
				return;
			}
		}
		
		// check for merchant's offer (and decline it):
		seg = detectCue(cues.get("Merchant"));
		if (seg != null) {
			BHBot.log("Legendary merchant offer? Taking screenshot for science.");
			try {
				saveGameScreen("merchant");
			} catch (Exception e) {
				// ignore it
			}
			
			seg = detectCue(cues.get("Decline"), 4 * SECOND);
			clickOnSeg(seg);
			
			readScreen(SECOND);
			seg = detectCue(cues.get("YesGreen"), 4 * SECOND);
			clickOnSeg(seg);
			return;
		}
		
		// check if we're done (cleared):
		seg = detectCue(cues.get("Cleared"));
		if (seg != null) {
			readScreen(500);
			closePopupSecurely(cues.get("Cleared"), cues.get("YesGreen"));
			
			// close the raid/dungeon/trials/gauntlet window:
			readScreen(4 * SECOND);
			// seg = detectCue(cues.get("X"), 14 * SECOND);
			//sleep(2 * SECOND); // because the popup window may still be sliding down so the "X" button is changing position and we must wait for it to stabilize
			seg = detectCue(cues.get("X"));
			if (seg != null)
				clickOnSeg(seg);
			else
				BHBot.log("Error: unable to find 'X' button to close raid/dungeon/trials/gauntlet window. Ignoring...");
			
			sleep(500);
			BHBot.log(state.getName() + " completed. Result: Victory");
			if (BHBot.settings.resetTimersOnBattleEnd) resetTimers();
			
			if (state == State.Raid)
				raidType = 0;
			
			state = State.Main; // reset state
			return;
		}
		
		// check if we're done (defeat):
		seg = detectCue(cues.get("Defeat"));
		if (seg != null) {
			readScreen(500);
			boolean closed = false;
			// close button in dungeons is blue, in gauntlet it is green:
			seg = detectCue(cues.get("Close"));
			if (seg != null) {
				clickOnSeg(seg);
				closed = true;
			}
			
			// close button in gauntlet:
			seg = detectCue(cues.get("CloseGreen"));
			if (seg != null) {
				clickOnSeg(seg);
				closed = true;
			}
			
			if (!closed) {
				BHBot.log("Problem: 'Defeat' popup detected but no 'Close' button detected. Ignoring...");
				if (state == State.PVP || state == State.GVG) dressUp();
				return;
			}
			
			// close the raid/dungeon/trials/gauntlet window:
			readScreen(4 * SECOND);
			// seg = detectCue(cues.get("X"), 14 * SECOND);
			//sleep(2 * SECOND); // because the popup window may still be sliding down so the "X" button is changing position and we must wait for it to stabilize
			seg = detectCue(cues.get("X"));
			if (seg != null)
				clickOnSeg(seg);
			else
				BHBot.log("Error: unable to find 'X' button to close raid/dungeon/trials/gauntlet window. Ignoring...");
			sleep(2 * SECOND);
			BHBot.log(state.getName() + " completed. Result: DEFEAT");
			if (BHBot.settings.resetTimersOnBattleEnd) resetTimers();
			if (state == State.PVP || state == State.GVG) {
				dressUp();
				/*
				// counting for GVG wins and loss
				GVGDefeats++;
				GVGVictoryPercentage = (byte) Math.round(100 * (GVGVictories /(GVGVictories + GVGDefeats)));
				BHBot.log("GvG victory percentage: " + GVGVictoryPercentage);
				*/
			}
			/*
			// counting for world boss wins and loss
			if (state == State.WorldBoss) {
				WBDefeats++;
				// this is fucked. it makes the % 0 every time
				WBVictoryPercentage = (byte) Math.round(100 * (WBVictories / (WBVictories + WBDefeats)));
				BHBot.log("World Boss victory percentage: " + WBVictoryPercentage);
			}
			*/
			
			if (state == State.Raid)
				raidType = 0;
			
			state = State.Main; // reset state
			return;
		}
		
		// check if we're done (victory - found in gauntlet, for example):
		seg = detectCue(cues.get("Victory"));
		if (seg != null) {
			readScreen(2 * SECOND);
			seg = detectCue(cues.get("CloseGreen")); // we need to wait a little bit because the popup scrolls down and the close button is semi-transparent (it stabilizes after popup scrolls down and bounces a bit)
			if (seg != null)
				clickOnSeg(seg);
			else {
				BHBot.log("Problem: 'Victory' window (as found in e.g. gauntlets) has been detected, but no 'Close' button. Ignoring...");
				return;
			}
			
			// close the raid/dungeon/trial/gauntlet window:
			readScreen(4 * SECOND);
			// seg = detectCue(cues.get("X"), 14 * SECOND);
			//sleep(2 * SECOND); // because the popup window may still be sliding down so the "X" button is changing position and we must wait for it to stabilize
			seg = detectCue(cues.get("X"));
			if (seg != null)
				clickOnSeg(seg);
			else
				BHBot.log("Error: unable to find 'X' button to close raid/dungeon/trials/gauntlet window. Ignoring...");
			
			sleep(2 * SECOND);
			BHBot.log(state.getName() + " completed. Result: Victory");
			if (BHBot.settings.resetTimersOnBattleEnd) resetTimers();
			if (state == State.GVG) {
				dressUp();
				/*
				// counting for GVG wins and loss
				GVGDefeats++;
				GVGVictoryPercentage = (byte) Math.round(100 * (GVGVictories /(GVGVictories + GVGDefeats)));
				BHBot.log("GvG victory percentage: " + GVGVictoryPercentage);
				*/
			}
			state = State.Main; // reset state
			return;
		}
		
		// 
		/*
		 * Check if we're done (victory in PVP mode) - this may also close local fights in dungeons, this is why we check if state is State.PVP and react only to that one.
		 * There were some crashing and clicking on SHOP problems with this one in dungeons and raids (and possibly elsewhere).
		 * Hence I fixed it by checking if state==State.PVP.
		 * Also needed in World Bosses.
		 */
		if (state == State.PVP || state == State.WorldBoss) {
			seg = detectCue(cues.get("VictoryPopup"));
			if (seg != null) {
				closePopupSecurely(cues.get("VictoryPopup"), cues.get("CloseGreen")); // ignore failure
				
				// close window, in case it is open:
				if (state == State.WorldBoss) {
					seg = detectCue(cues.get("X"), 4 * SECOND);
					clickOnSeg(seg);
					
					sleep(SECOND);
					BHBot.log(state.getName() + " completed. Result: Victory");
					if (BHBot.settings.resetTimersOnBattleEnd) resetTimers();
					state = State.Main; // reset state
					/*
					// counting percentage for WB wins
					WBVictories++;
					// something fucked here? i only get 100% till now
					WBVictoryPercentage = (byte) Math.round(100 * (WBVictories /(WBVictories + WBDefeats)));
					BHBot.log("World Boss victory percentage: " + WBVictoryPercentage);
					*/
					return;
				} else {
					readScreen(4 * SECOND);
					seg = detectCue(cues.get("PVPWindow"));
					if (seg != null)
						closePopupSecurely(cues.get("PVPWindow"), cues.get("X")); // ignore failure
					sleep(SECOND);
					BHBot.log(state.getName() + " completed. Result: Victory");
					if (BHBot.settings.resetTimersOnBattleEnd) resetTimers();
					dressUp();
					state = State.Main; // reset state
					
					/*
					// counting percentage for PVP wins
					PVPVictories++;
					PVPVictoryPercentage = (byte) Math.round(100 * (PVPVictories /(PVPVictories + PVPDefeats)));
					BHBot.log("PvP victory percentage: " + WBVictoryPercentage);
					*/
					
					return;
				}
			}
		}
		
		// at the end of this method, revert idle time change (in order for idle detection to function properly):
		BHBot.scheduler.restoreIdleTime();
	}
	
	/**
	 * @param dungeon in standard format, e.g. "z2d4".
	 * @return null in case dungeon parameter is malformed (can even throw an exception)
	 */
	private Point getDungeonIconPos(String dungeon) {
		if (dungeon.length() != 4) return null;
		if (dungeon.charAt(0) != 'z') return null;
		if (dungeon.charAt(2) != 'd') return null;
		int z = Integer.parseInt("" + dungeon.charAt(1));
		int d = Integer.parseInt("" + dungeon.charAt(3));
		if (z < 1 || z > 7) return null;
		if (d < 1 || d > 4) return null;
		
		switch (z) {
			case 1: // zone 1
				switch (d) {
					case 1:
						return new Point(240, 350);
					case 2:
						return new Point(580, 190);
					case 3:
						return new Point(660, 330);
					case 4:
						return new Point(410, 230);
				}
				break;
			case 2: // zone 2
				switch (d) {
					case 1:
						return new Point(215, 270);
					case 2:
						return new Point(550, 150);
					case 3:
						return new Point(515, 380);
					case 4:
						return new Point(400, 270);
				}
				break;
			case 3: // zone 3
				switch (d) {
					case 1:
						return new Point(145, 200);
					case 2:
						return new Point(430, 300);
					case 3:
						return new Point(565, 375);
					case 4:
						return new Point(570, 170);
				}
				break;
			case 4: // zone 4
				switch (d) {
					case 1:
						return new Point(300, 400);
					case 2:
						return new Point(260, 200);
					case 3:
						return new Point(650, 200);
					case 4:
						return new Point(400, 270);
				}
				break;
			case 5: // zone 5
				switch (d) {
					case 1:
						return new Point(150, 200);
					case 2:
						return new Point(410, 380);
					case 3:
						return new Point(630, 240);
					case 4:
						return new Point(550, 150);
				}
				break;
			case 6: // zone 6
				switch (d) {
					case 1:
						return new Point(205, 270);
					case 2:
						return new Point(515, 380);
					case 3:
						return new Point(555, 150);
					case 4:
						return new Point(400, 270);
				}
				break;
			case 7: // zone 7
				switch (d) {
					case 1:
						return new Point(242, 352);
					case 2:
						return new Point(580, 190);
					case 3:
						return null;
					case 4:
						return new Point(400, 270);
				}
				break;
		}
		
		return null;
	}
	
	/**
	 * Returns dungeon and difficulty level, e.g. 'z2d4 2'.
	 */
	private String decideDungeonRandomly() {
		if (BHBot.settings.dungeons.size() == 0)
			return "z2d4 3";
		
		int total = 0;
		for (String d : BHBot.settings.dungeons)
			total += Integer.parseInt(d.split(" ")[2]);
		
		int rand = (int) Math.round(Math.random() * total);
		
		int value = 0;
		for (String d : BHBot.settings.dungeons) {
			value += Integer.parseInt(d.split(" ")[2]);
			if (value >= rand)
				return d.split(" ")[0] + " " + d.split(" ")[1];
		}
		
		return null; // should not come to this
	}
	
	/**
	 * Returns raid type (1, 2 or 3) and difficulty level (1, 2 or 3, which correspond to normal, hard and heroic), e.g. '1 3'.
	 */
	private String decideRaidRandomly() {
		if (BHBot.settings.raids.size() == 0)
			return "1 3";
		
		int total = 0;
		for (String r : BHBot.settings.raids)
			total += Integer.parseInt(r.split(" ")[2]);
		
		int rand = (int) Math.round(Math.random() * total);
		
		int value = 0;
		for (String r : BHBot.settings.raids) {
			value += Integer.parseInt(r.split(" ")[2]);
			if (value >= rand)
				return r.split(" ")[0] + " " + r.split(" ")[1];
		}
		
		return null; // should not come to this
	}
	
	/**
	 * Returns number of zone that is currently selected in the quest window (we need to be in the quest window for this to work).
	 * Returns 0 in case zone could not be read (in case we are not in the quest window, for example).
	 */
	private int readCurrentZone() {
		if (detectCue(cues.get("Zone4")) != null)
			return 4;
		else if (detectCue(cues.get("Zone3")) != null)
			return 3;
		else if (detectCue(cues.get("Zone5")) != null)
			return 5;
		else if (detectCue(cues.get("Zone2")) != null)
			return 2;
		else if (detectCue(cues.get("Zone1")) != null)
			return 1;
		else if (detectCue(cues.get("Zone6")) != null)
			return 6;
		else if (detectCue(cues.get("Zone7")) != null)
			return 7;
		else
			return 0;
	}
	
	/**
	 * Returns raid type, that is value between 1 and 3 (1 stands for R1, 2 for R2, 3 for R3) that is currently selected in the raid window.
	 * Note that the raid window must be open for this method to work (or else it will simply return 0).
	 */
	// Jesus...
	// fuck it, just fixing. could make a method or something to organize it. or scrap the whole thing later.
	private int readCurrentRaidType() {
		MarvinSegment seg = detectCue(cues.get("RaidLevel"));
		if (seg == null) {
			// either we don't have R2 open yet (hence there is not selection button) or an error occured:
			seg = detectCue(cues.get("R1Only"));
			return seg != null ? 1 : 0;
		}
		
		final Color off = new Color(147, 147, 147); // color of center pixel of turned off button
		
		Point center = new Point(seg.x1 + 7, seg.y1 + 7); // center of the raid button (green, small one)
		Point right = center.moveBy(25, 0); // this point is the middle of the button to left of current green one
		Point rightDouble = center.moveBy(50, 0);
		Point rightTriple = center.moveBy(75, 0);
		Point rightQuadruple = center.moveBy(100, 0);
		Point left = center.moveBy(-25, 0); // one button to the left
		Point leftDouble = center.moveBy(-50, 0);
		Point leftTriple = center.moveBy(-75, 0);
		Point leftQuadruple = center.moveBy(-100, 0);
		
		// Am i in raid 4?
		boolean r4 = (new Color(img.getRGB(leftTriple.x, leftTriple.y))).equals(off) && (new Color(img.getRGB(right.x, right.y))).equals(off);
		
		// ifs are ordered from most used to least.
		if (r4)
			return 4;
		else if ((new Color(img.getRGB(leftQuadruple.x, leftQuadruple.y))).equals(off))
			return 5;
		else if ((new Color(img.getRGB(leftDouble.x, leftDouble.y))).equals(off) && (new Color(img.getRGB(rightDouble.x, rightDouble.y))).equals(off))
			return 3;
		else if ((new Color(img.getRGB(left.x, left.y))).equals(off) && (new Color(img.getRGB(rightTriple.x, rightTriple.y))).equals(off))
			return 2;
		else if ((new Color(img.getRGB(rightQuadruple.x, rightQuadruple.y))).equals(off))
			return 1;
		else
			return 0; // error
	}
	
	/**
	 * Note: raid window must be open for this to work!
	 * <p>
	 * Returns false in case it failed.
	 */
	// God help us all if we get many raids. These need to be changed...
	private boolean setRaidType(int newType, int currentType) {
		final Color off = new Color(147, 147, 147); // color of center pixel of turned off button
		
		MarvinSegment seg = detectCue(cues.get("RaidLevel"));
		if (seg == null) {
			// error!
			BHBot.log("Error: Changing of raid type failed - raid type button not detected.");
			return false;
		}
		
		Point center = new Point(seg.x1 + 7, seg.y1 + 7); // center of the raid button
		int move = newType - currentType;
		Point pos = center.moveBy(move * 25, 0);
		
		clickInGame(pos.x, pos.y);
		
		return true;
	}
	
	/**
	 * Takes screenshot of current game and saves it to disk to a file with a given prefix (date will be added, and optionally a number at the end of file name).
	 * In case of failure, it will just ignore the error.
	 *
	 * @return name of the file to which the screenshot has been saved (successfully or not)
	 */
	private String saveGameScreen(String prefix) {
		Date date = new Date();
		String name = prefix + "_" + dateFormat.format(date) + ".png";
		int num = 0;
		File f = new File(name);
		while (f.exists()) {
			num++;
			name = prefix + "_" + dateFormat.format(date) + "_" + num + ".png";
			f = new File(name);
		}
		
		// save screen shot:
		try {
			Shutterbug.shootElement(driver, driver.findElement(By.id("game")), false).withName(name.substring(0, name.length() - 4)).save(".");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return name;
	}
	
	/**
	 * Will detect and handle (close) in-game private message (from the current screen capture). Returns true in case PM has been handled.
	 */
	private boolean handlePM() {
		if (detectCue(cues.get("InGamePM")) != null) {
			MarvinSegment seg = detectCue(cues.get("X"), 4 * SECOND);
			if (seg == null) {
				BHBot.log("Error: in-game PM window detected, but no close button found. Restarting...");
				try {
					saveGameScreen("pm");
				} catch (Exception e) {
					// ignore it
				}
				restart(); //*** problem: after a call to this, it will return to the main loop. It should call "continue" inside the main loop or else there could be other exceptions!
				return true;
			}
			
			try {
				saveGameScreen("pm");
				clickOnSeg(seg);
			} catch (Exception e) {
				// ignore it
			}
			return true;
		} else {
			return false; // no PM detected
		}
	}
	
	/**
	 * Handles popup that tells you that your team is not complete. Happens when some friend left you.
	 * This method will attempt to click on "Auto" button to refill your team.
	 * Note that this can happen in raid and GvG only, since in other games (PvP, Gauntlet/Trials) you can use only familiars.
	 * In GvG, on the other hand, there is additional dialog possible (which is not possible in raid): "team not ordered" dialog.
	 *
	 * @return true in case emergency restart is needed.
	 */
	private boolean handleTeamMalformedWarning() {
		readScreen();
		if (detectCue(cues.get("TeamNotFull")) != null || detectCue(cues.get("TeamNotOrdered")) != null) {
			sleep(500); // in case popup is still sliding downward
			readScreen();
			MarvinSegment seg = detectCue(cues.get("No"), 4 * SECOND);
			if (seg == null) {
				BHBot.log("Error: 'Team not full/ordered' window detected, but no 'No' button found. Restarting...");
				return true;
			}
			clickOnSeg(seg);
			sleep(2 * SECOND);
			
			seg = detectCue(cues.get("AutoTeam"), 10 * SECOND);
			if (seg == null) {
				BHBot.log("Error: 'Team not full/ordered' window detected, but no 'Auto' button found. Restarting...");
				return true;
			}
			clickOnSeg(seg);
			
			readScreen(4 * SECOND);
			seg = detectCue(cues.get("Accept"));
			if (seg == null) {
				BHBot.log("Error: 'Team not full/ordered' window detected, but no 'Accept' button found. Restarting...");
				return true;
			}
			clickOnSeg(seg);
			
			BHBot.log("'Team not full/ordered' dialog detected and handled - team has been auto assigned!");
		}
		
		return false; // all OK
	}
	
	/**
	 * Will check if "Not enough energy" popup is open. If it is, it will automatically close it and close all other windows
	 * until it returns to the main screen.
	 *
	 * @return true in case popup was detected and closed.
	 */
	private boolean handleNotEnoughEnergyPopup() {
		MarvinSegment seg = detectCue(cues.get("NotEnoughEnergy"));
		if (seg != null) {
			// we don't have enough energy!
			BHBot.log("Problem detected: insufficient energy to attempt dungeon. Cancelling...");
			closePopupSecurely(cues.get("NotEnoughEnergy"), cues.get("No"));
			// close team window:
			closePopupSecurely(cues.get("AutoTeam"), cues.get("X"));
			// close difficulty selection screen:
			seg = detectCue(cues.get("Enter"));
			if (seg != null) {
				closePopupSecurely(cues.get("Enter"), cues.get("X"));
			} else {
				closePopupSecurely(cues.get("Normal"), cues.get("X"));
			}
			// close zone view window:
			closePopupSecurely(cues.get("ZonesButton"), cues.get("X"));
			
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Reads number from given image.
	 *
	 * @return 0 in case of error.
	 */
	private int readNumFromImg(BufferedImage im) {
		List<ScreenNum> nums = new ArrayList<ScreenNum>();
		
		//MarvinImageIO.saveImage(im, "difficulty_test.png");
		//Misc.saveImage(imb, "difficulty_test2.png");
		for (int i = 0; i < 10; i++) {
			List<MarvinSegment> list = FindSubimage.findSubimage(im, cues.get("" + i).im, 1.0, true, false, 0, 0, 0, 0);
			//BHBot.log("DEBUG difficulty detection: " + i + " - " + list.size());
			for (MarvinSegment s : list) {
				nums.add(new ScreenNum(i, s.x1));
			}
		}
		
		// order list horizontally:
		Collections.sort(nums);
		
		if (nums.size() == 0)
			return 0; // error
		
		int d = 0; // difficulty
		int f = 1; // factor
		for (int i = nums.size() - 1; i >= 0; i--) {
			d += nums.get(i).value * f;
			f *= 10;
		}
		
		return d;
	}
	
	private void makeImageBlackWhite(MarvinImage input, Color black, Color white) {
		int[] map = input.getIntColorArray();
		int white_rgb = white.getRGB();
		int black_rgb = black.getRGB();
		for (int i = 0; i < map.length; i++) {
			Color c = new Color(map[i], true);
			int r = c.getRed();
			int g = c.getGreen();
			int b = c.getBlue();
			int max = Misc.max(r, g, b);
			int min = Misc.min(r, g, b);
			//int diff = (max-r) + (max-g) + (max-b);
			int diff = max - min;
			if (diff >= 90 || (diff == 0 && max == 254)) { // it's a number color
				map[i] = white_rgb;
			} else { // it's a blackish background
				map[i] = black_rgb;
			}
		}
		input.setIntColorArray(map);
		input.update(); // must be called! Or else things won't work...
	}
	
	/**
	 * Detects selected difficulty in trials/gauntlet window. <br>
	 * NOTE: Trials/gauntlet window must be open for this to work! <br>
	 *
	 * @return 0 in case of an error, or the selected difficulty level instead.
	 */
	public int detectDifficulty() {
		sleep(2 * SECOND); // note that sometimes the cue will be gray (disabled) since the game is fetching data from the server - in that case we'll have to wait a bit
		
		MarvinSegment seg = detectCue(cues.get("Difficulty"));
		if (seg == null) {
			seg = detectCue(cues.get("DifficultyDisabled"));
			if (seg != null) { // game is still fetching data from the server... we must wait a bit!
				sleep(4 * SECOND);
				seg = detectCue(cues.get("Difficulty"), 20 * SECOND);
			}
		}
		if (seg == null) {
			BHBot.log("Error: unable to detect difficulty selection box!");
			saveGameScreen("early_error");
			return 0; // error
		}
		
		MarvinImage im = new MarvinImage(img.getSubimage(seg.x1 + 35, seg.y1 + 30, 55, 19));
		
		// make it white-gray (to facilitate cue recognition):
		makeImageBlackWhite(im, new Color(25, 25, 25), new Color(255, 255, 255));
		
		BufferedImage imb = im.getBufferedImage();
		int d = readNumFromImg(imb);
		
		return d;
	}
	
	/**
	 * Changes difficulty level in trials/gauntlet window. <br>
	 * Note: for this to work, trials/gauntlet window must be open!
	 *
	 * @return false in case of an error (unable to change difficulty).
	 */
	public boolean selectDifficulty(int oldDifficulty, int newDifficulty) {
		if (oldDifficulty == newDifficulty)
			return true; // no change
		
		MarvinSegment seg = detectCue(cues.get("SelectDifficulty"), 4 * SECOND);
		if (seg == null) {
			BHBot.log("Error: unable to detect 'select difficulty' button while trying to change difficulty level!");
			return false; // error
		}
		
		clickOnSeg(seg);
		
		readScreen(3 * SECOND);
		
		return selectDifficultyFromDropDown(oldDifficulty, newDifficulty);
	}
	
	/**
	 * Internal routine. Difficulty drop down must be open for this to work!
	 * Note that it closes the drop-down when it is done (except if an error occurred). However there is a close
	 * animation and the caller must wait for it to finish.
	 *
	 * @return false in case of an error.
	 */
	private boolean selectDifficultyFromDropDown(int oldDifficulty, int newDifficulty) {
		return selectDifficultyFromDropDown(oldDifficulty, newDifficulty, 0);
	}
	
	/**
	 * Internal routine - do not use it manually! <br>
	 *
	 * @return false on error (caller must do restart() if he gets false as a result from this method)
	 */
	private boolean selectDifficultyFromDropDown(int oldDifficulty, int newDifficulty, int recursionDepth) {
		// horizontal position of the 5 buttons:
		final int posx = 390;
		// vertical positions of the 5 buttons:
		final int posy[] = new int[]{170, 230, 290, 350, 410};
		
		if (recursionDepth > 5) {
			BHBot.log("Error: Selecting difficulty level from the drop-down menu ran into an endless loop!");
			saveGameScreen("early_error");
			tryClosingWindow(); // clean up after our selves (ignoring any exception while doing it)
			return false;
		}
		
		MarvinSegment seg;
		
		MarvinImage subm = new MarvinImage(img.getSubimage(350, 150, 70, 35)); // the first (upper most) of the 5 buttons in the drop-down menu. Note that every while a "tier x" is written bellow it, so text is higher up (hence we need to scan a larger area)
		makeImageBlackWhite(subm, new Color(25, 25, 25), new Color(255, 255, 255));
		BufferedImage sub = subm.getBufferedImage();
		int num = readNumFromImg(sub);
		if (num == 0) {
			BHBot.log("Error: unable to read difficulty level from a drop-down menu!");
			saveGameScreen("early_error");
			tryClosingWindow(); // clean up after our selves (ignoring any exception while doing it)
			return false;
		}
		
		int move = newDifficulty - num; // if negative, we have to move down (in dropdown/numbers), or else up
		
		if (move >= -4 && move <= 0) {
			// we have it on screen. Let's select it!
			clickInGame(posx, posy[Math.abs(move)]); // will auto-close the drop down (but it takes a second or so, since it's animated)
			return true;
		}
		
		// scroll the drop-down until we reach our position:
		if (move > 0) {
			// move up
			seg = detectCue(cues.get("DropDownUp"));
			if (seg == null) {
				BHBot.log("Error: unable to detect up arrow in trials/gauntlet difficulty drop-down menu!");
				saveGameScreen("early_error");
				clickInGame(posx, posy[0]); // regardless of the error, click on the first selection in the drop-down, so that we don't need to re-scroll entire list next time we try!
				return false;
			}
			for (int i = 0; i < move; i++) {
				clickOnSeg(seg);
			}
			// OK, we should have a target value on screen now, in the first spot. Let's click it!
			readScreen(3 * SECOND); //*** should we increase this time?
			return selectDifficultyFromDropDown(oldDifficulty, newDifficulty, recursionDepth + 1); // recursively select new difficulty
		} else if (true) {
			// move down
			seg = detectCue(cues.get("DropDownDown"));
			if (seg == null) {
				BHBot.log("Error: unable to detect down arrow in trials/gauntlet difficulty drop-down menu!");
				saveGameScreen("early_error");
				clickInGame(posx, posy[0]); // regardless of the error, click on the first selection in the drop-down, so that we don't need to re-scroll entire list next time we try!
				return false;
			}
			int moves = Math.abs(move) - 4;
			for (int i = 0; i < moves; i++) {
				clickOnSeg(seg);
			}
			// OK, we should have a target value on screen now, in the first spot. Let's click it!
			readScreen(3 * SECOND); //*** should we increase this time?
			return selectDifficultyFromDropDown(oldDifficulty, newDifficulty, recursionDepth + 1); // recursively select new difficulty
		} else {
			assert false; // must not happen (we check for 0 earlier on)
			return false;
		}
	}
	
	//*** for DEBUG only!
	public void numTest() {
		MarvinSegment seg;
		
		while (true) {
			readScreen(500);
			
			MarvinImage subm = new MarvinImage(img.getSubimage(350, 150, 70, 35)); // the first (upper most) of the 5 buttons in the drop-down menu
			makeImageBlackWhite(subm, new Color(25, 25, 25), new Color(255, 255, 255));
			BufferedImage sub = subm.getBufferedImage();
			int num = readNumFromImg(sub);
			if (num == 0) {
				BHBot.log("Error: unable to read difficulty level from a drop-down menu!");
				return;
			}
			BHBot.log("Difficulty: " + num);
			
			// move up
			seg = detectCue(cues.get("DropDownUp"));
			if (seg == null) {
				BHBot.log("Error: unable to detect up arrow in trials/gauntlet difficulty drop-down menu!");
				return;
			}
			clickOnSeg(seg);
		}
	}
	
	/**
	 * This method detects the select cost in PvP/GvG/Trials/Gauntlet window. <p>
	 * <p>
	 * Note: PvP cost has different position from GvG/Gauntlet/Trials. <br>
	 * Note: PvP/GvG/Trials/Gauntlet window must be open in order for this to work!
	 *
	 * @return 0 in case of an error, or cost value in interval [1..5]
	 */
	public int detectCost() {
		MarvinSegment seg = detectCue(cues.get("Cost"), 14 * SECOND);
		if (seg == null) {
			BHBot.log("Error: unable to detect cost selection box!");
			saveGameScreen("early_error");
			return 0; // error
		}
		
		// because the popup may still be sliding down and hence cue could be changing position, we try to read cost in a loop (until a certain timeout):
		int d = 0;
		int counter = 0;
		boolean success = true;
		while (true) {
			MarvinImage im = new MarvinImage(img.getSubimage(seg.x1 + 2, seg.y1 + 20, 35, 24));
			makeImageBlackWhite(im, new Color(25, 25, 25), new Color(255, 255, 255));
			BufferedImage imb = im.getBufferedImage();
			d = readNumFromImg(imb);
			if (d != 0)
				break; // success
			
			counter++;
			if (counter > 10) {
				success = false;
				break;
			}
			sleep(SECOND); // sleep a bit in order for the popup to slide down
			readScreen();
			seg = detectCue(cues.get("Cost"));
		}
		
		if (!success) {
			BHBot.log("Error: unable to detect cost selection box value!");
			saveGameScreen("early_error");
			return 0;
		}
		
		return d;
	}
	
	/**
	 * Changes cost in PvP, GvG, or Trials/Gauntlet window. <br>
	 * Note: for this to work, PvP/GvG/Trials/Gauntlet window must be open!
	 *
	 * @return false in case of an error (unable to change cost).
	 */
	public boolean selectCost(int oldCost, int newCost) {
		if (oldCost == newCost)
			return true; // no change
		
		MarvinSegment seg = detectCue(cues.get("SelectCost"), 4 * SECOND);
		if (seg == null) {
			BHBot.log("Error: unable to detect 'select cost' button while trying to change cost!");
			return false; // error
		}
		
		clickOnSeg(seg);
		
		readScreen(3 * SECOND); // wait for the cost selection popup window to open
		
		// horizontal position of the 5 buttons:
		final int posx = 390;
		// vertical positions of the 5 buttons:
		final int posy[] = new int[]{170, 230, 290, 350, 410};
		
		clickInGame(posx, posy[newCost - 1]); // will auto-close the drop down (but it takes a second or so, since it's animated)
		sleep(2 * SECOND);
		
		return true;
	}
	
	/**
	 * Will try to click on "X" button of the currently open popup window. On error, it will ignore it. <br>
	 * NOTE: This method does not re-read screen before (or after) cue detection!
	 */
	private void tryClosingWindow() {
		tryClosingWindow(null);
	}
	
	/**
	 * Will try to click on "X" button of the currently open popup window that is identified by the 'windowCue'. It will ignore any errors. <br>
	 * NOTE: This method does not re-read screen before (or after) cue detection!
	 */
	private void tryClosingWindow(Cue windowCue) {
		try {
			MarvinSegment seg;
			if (windowCue != null) {
				seg = detectCue(windowCue);
				if (seg == null)
					return;
			}
			seg = detectCue(cues.get("X"));
			if (seg != null)
				clickOnSeg(seg);
		} catch (Exception e) {e.printStackTrace();
		}
	}
	
	/**
	 * Will close the popup by clicking on the 'close' cue and checking that 'popup' cue is gone. It will repeat this operation
	 * until either 'popup' cue is gone or timeout is reached. This method ensures that the popup is closed. Sometimes just clicking once
	 * on the close button ('close' cue) doesn't work, since popup is still sliding down and we miss the button, this is why we need to
	 * check if it is actually closed. This is what this method does.
	 * <p>
	 * Note that before entering into this method, caller had probably already detected the 'popup' cue (but not necessarily). <br>
	 * Note: in case of failure, it will print it out.
	 *
	 * @return false in case it failed to close it (timed out).
	 */
	private boolean closePopupSecurely(Cue popup, Cue close) {
		MarvinSegment seg1, seg2;
		int counter;
		seg1 = detectCue(close);
		seg2 = detectCue(popup);
		
		// make sure popup window is on the screen (or else wait until it appears):
		counter = 0;
		while (seg2 == null) {
			counter++;
			if (counter > 10) {
				BHBot.log("Error: unable to close popup <" + popup.name + "> securely: popup cue not detected!");
				return false;
			}
			readScreen(500);
			seg2 = detectCue(popup);
		}
		
		counter = 0;
		while (true) {
			if (seg2 == null)
				break; // there is no more popup window, so we're finished!
			if (seg1 != null)
				clickOnSeg(seg1);
			
			counter++;
			if (counter > 10) {
				BHBot.log("Error: unable to close popup <" + popup.name + "> securely: either close button has not been detected or popup would not close!");
				return false;
			}
			
			readScreen(500);
			seg1 = detectCue(close);
			seg2 = detectCue(popup);
		}
		
		return true;
	}
	
	/**
	 * @return -1 on error
	 */
	private int detectEquipmentFilterScrollerPos() {
		final int[] yScrollerPositions = {146, 165, 187, 207, 227, 247, 267, 288, 309, 329}; // top scroller positions
		
		MarvinSegment seg = detectCue(cues.get("StripScrollerTopPos"), 2 * SECOND);
		if (seg == null) {
			return -1;
		}
		int pos = seg.y1;
		
		return Misc.findClosestMatch(yScrollerPositions, pos);
	}
	
	/**
	 * Will strip character down (as a preparation for the PvP battle) of items passed as parameters to this method.
	 * Note that before calling this method, game must be in the main method!
	 *
	 * @param type which item type should we equip/unequip
	 * @param dir  direction - either strip down or dress up
	 */
	private void strip(EquipmentType type, StripDirection dir) {
		MarvinSegment seg;
		
		// click on the character menu button (it's a bottom-left button with your character image on it):
		clickInGame(55, 465);
		
		seg = detectCue(cues.get("StripSelectorButton"), 8 * SECOND);
		if (seg == null) {
			BHBot.log("Error: unable to detect equipment filter button! Skipping...");
			return;
		}
		
		// now lets see if the right category is already selected:
		seg = detectCue(type.getCue(), SECOND);
		if (seg == null) {
			// OK we need to manually select the correct category!
			seg = detectCue(cues.get("StripSelectorButton"));
			clickOnSeg(seg);
			
			seg = detectCue(cues.get("StripItemsTitle"), 8 * SECOND); // waits until "Items" popup is detected
			readScreen(300); // to stabilize sliding popup a bit
			
			int scrollerPos = detectEquipmentFilterScrollerPos();
			if (scrollerPos == -1) {
				BHBot.log("Problem detected: unable to detect scroller position in the character window (location #1)! Skipping strip down/up...");
				return;
			}
			
			int[] yButtonPositions = {170, 230, 290, 350, 410}; // center y positions of the 5 buttons
			int xButtonPosition = 390;
			
			if (scrollerPos >= type.minPos() && scrollerPos <= type.maxPos()) {
				// scroller is already at the right position!
			} else if (scrollerPos < type.minPos()) {
				// we must scroll down!
				int move = type.minPos() - scrollerPos;
				seg = detectCue(cues.get("DropDownDown"), 4 * SECOND);
				for (int i = 0; i < move; i++) {
					clickOnSeg(seg);
					scrollerPos++;
				}
			} else { // bestIndex > type.maxPos
				// we must scroll up!
				int move = scrollerPos - type.minPos();
				seg = detectCue(cues.get("DropDownUp"), 4 * SECOND);
				for (int i = 0; i < move; i++) {
					clickOnSeg(seg);
					scrollerPos--;
				}
			}
			
			// make sure scroller is in correct position now:
			readScreen(400); // so that the scroller stabilizes a bit
			int newScrollerPos = detectEquipmentFilterScrollerPos();
			int counter = 0;
			while (newScrollerPos != scrollerPos) {
				if (counter > 3) {
					BHBot.log("Problem detected: unable to adjust scroller position in the character window (scroller position: " + newScrollerPos + ", should be: " + scrollerPos + ")! Skipping strip down/up...");
					return;
				}
				readScreen(600);
				newScrollerPos = detectEquipmentFilterScrollerPos();
				counter++;
			}
			clickInGame(xButtonPosition, yButtonPositions[type.getButtonPos() - scrollerPos]);
			// clicking on the button will close the window automatically... we just need to wait a bit for it to close
			seg = detectCue(cues.get("StripSelectorButton"), 4 * SECOND); // we do this just in order to wait for the previous menu to reappear
		}
		
		waitForInventoryIconsToLoad(); // first of all, lets make sure that all icons are loaded
		
		// now deselect/select the strongest equipment in the menu:
		
		seg = detectCue(cues.get("StripEquipped"), 500); // if "E" icon is not found, that means that some other item is equipped or that no item is equipped
		boolean equipped = seg != null; // is strongest item equipped already?
		
		// position of top-left item (which is the strongest) is (490, 210)
		if (dir == StripDirection.StripDown) {
			clickInGame(490, 210);
			if (!equipped) // in case item was not equipped, we must click on it twice, first time to equip it, second to unequip it. This could happen for example when we had some weaker item equipped (or no item equipped).
				clickInGame(490, 210);
		} else {
			if (!equipped)
				clickInGame(490, 210);
		}
		
		// OK, we're done, lets close the character menu window:
		closePopupSecurely(cues.get("StripSelectorButton"), cues.get("X"));
	}
	
	private void stripDown() {
		if (BHBot.settings.pvpStrip.size() == 0) {
			BHBot.log("pvpStrip is empty, cant strip");
			return;
		}
		
		String list = "";
		for (String type : BHBot.settings.pvpStrip) {
			list += EquipmentType.letterToName(type) + ", ";
		}
		list = list.substring(0, list.length() - 2);
		BHBot.log("Stripping down for PvP/GvG (" + list + ")...");
		
		for (String type : BHBot.settings.pvpStrip) {
			strip(EquipmentType.letterToType(type), StripDirection.StripDown);
		}
	}
	
	private void dressUp() {
		if (BHBot.settings.pvpStrip.size() == 0)
			return;
		
		String list = "";
		for (String type : BHBot.settings.pvpStrip) {
			list += EquipmentType.letterToName(type) + ", ";
		}
		list = list.substring(0, list.length() - 2);
		BHBot.log("Dressing back up (" + list + ")...");
		
		for (String type : BHBot.settings.pvpStrip) {
			strip(EquipmentType.letterToType(type), StripDirection.DressUp);
		}
	}
	
	/**
	 * We must be in main menu for this to work!
	 */
	private void handleConsumables() {
		if (!BHBot.settings.autoConsume || BHBot.settings.consumables.size() == 0) // consumables management is turned off!
			return;
		
		MarvinSegment seg;
		
		boolean exp = detectCue(cues.get("BonusExp")) != null;
		boolean item = detectCue(cues.get("BonusItem")) != null;
		boolean speed = detectCue(cues.get("BonusSpeed")) != null;
		boolean gold = detectCue(cues.get("BonusGold")) != null;
		
		EnumSet<ConsumableType> duplicateConsumables = EnumSet.noneOf(ConsumableType.class); // here we store consumables that we wanted to consume now but we have detected they are already active, so we skipped them (used for error reporting)
		EnumSet<ConsumableType> consumables = EnumSet.noneOf(ConsumableType.class); // here we store consumables that we want to consume now
		for (String s : BHBot.settings.consumables)
			consumables.add(ConsumableType.getTypeFromName(s));
		//BHBot.log("Testing for following consumables: " + Misc.listToString(consumables));
		
		if (exp) {
			consumables.remove(ConsumableType.EXP_MINOR);
			consumables.remove(ConsumableType.EXP_AVERAGE);
			consumables.remove(ConsumableType.EXP_MAJOR);
		}
		
		if (item) {
			consumables.remove(ConsumableType.ITEM_MINOR);
			consumables.remove(ConsumableType.ITEM_AVERAGE);
			consumables.remove(ConsumableType.ITEM_MAJOR);
		}
		
		if (speed) {
			consumables.remove(ConsumableType.SPEED_MINOR);
			consumables.remove(ConsumableType.SPEED_AVERAGE);
			consumables.remove(ConsumableType.SPEED_MAJOR);
		}
		
		if (gold) {
			consumables.remove(ConsumableType.GOLD_MINOR);
			consumables.remove(ConsumableType.GOLD_AVERAGE);
			consumables.remove(ConsumableType.GOLD_MAJOR);
		}
		
		// so now we have only those consumables in the 'consumables' list that we actually need to consume right now!
		
		if (consumables.isEmpty()) // we don't need to do anything!
			return;
		
		// OK, try to consume some consumables!
		BHBot.log("Trying to consume some consumables (" + Misc.listToString(consumables) + ")...");
		
		// click on the character menu button (it's a bottom-left button with your character image on it):
		clickInGame(55, 465);
		
		seg = detectCue(cues.get("StripSelectorButton"), 8 * SECOND);
		if (seg == null) {
			BHBot.log("Error: unable to detect equipment filter button! Skipping...");
			return;
		}
		
		// now lets select the <Consumables> category (if it is not already selected):
		seg = detectCue(cues.get("FilterConsumables"), 500);
		if (seg == null) { // if not, right category (<Consumables>) is already selected!
			// OK we need to manually select the <Consumables> category!
			seg = detectCue(cues.get("StripSelectorButton"));
			clickOnSeg(seg);
			
			seg = detectCue(cues.get("StripItemsTitle"), 10 * SECOND); // waits until "Items" popup is detected
			readScreen(500); // to stabilize sliding popup a bit
			
			int scrollerPos = detectEquipmentFilterScrollerPos();
			if (scrollerPos == -1) {
				BHBot.log("Problem detected: unable to detect scroller position in the character window (location #1)! Skipping consumption of consumables...");
				return;
			}
			
			int[] yButtonPositions = {170, 230, 290, 350, 410}; // center y positions of the 5 buttons
			int xButtonPosition = 390;
			
			if (scrollerPos != 0) {
				// we must scroll up!
				int move = scrollerPos;
				seg = detectCue(cues.get("DropDownUp"), 4 * SECOND);
				for (int i = 0; i < move; i++) {
					clickOnSeg(seg);
					scrollerPos--;
				}
			}
			
			// make sure scroller is in correct position now:
			readScreen(400); // so that the scroller stabilizes a bit
			int newScrollerPos = detectEquipmentFilterScrollerPos();
			int counter = 0;
			while (newScrollerPos != scrollerPos) {
				if (counter > 3) {
					BHBot.log("Problem detected: unable to adjust scroller position in the character window (scroller position: " + newScrollerPos + ", should be: " + scrollerPos + ")! Skipping consumption of consumables...");
					return;
				}
				readScreen(500);
				newScrollerPos = detectEquipmentFilterScrollerPos();
				counter++;
			}
			clickInGame(xButtonPosition, yButtonPositions[1]);
			// clicking on the button will close the window automatically... we just need to wait a bit for it to close
			seg = detectCue(cues.get("StripSelectorButton"), 4 * SECOND); // we do this just in order to wait for the previous menu to reappear
		}
		
		// now consume the consumable(s):
		
		readScreen(500); // to stabilize window a bit
		Bounds bounds = new Bounds(450, 165, 670, 460); // detection area (where consumables icons are visible)
		
		while (!consumables.isEmpty()) {
			waitForInventoryIconsToLoad(); // first of all, lets make sure that all icons are loaded
			for (Iterator<ConsumableType> i = consumables.iterator(); i.hasNext(); ) {
				ConsumableType c = i.next();
				seg = detectCue(new Cue(c.getInventoryCue(), bounds));
				if (seg != null) {
					// OK we found the consumable icon! Lets click it...
					clickOnSeg(seg);
					detectCue(cues.get("ConsumableTitle"), 4 * SECOND); // wait for the consumable popup window to appear
					readScreen(500); // wait for sliding popup to stabilize a bit
					
					/*
					 *  Measure distance between "Consumable" (popup title) and "Yes" (green yes button).
					 *  This seems to be the safest way to distinguish the two window types. Because text
					 *  inside windows change and sometimes letters are wider apart and sometimes no, so it
					 *  is not possible to detect cue like "replace" wording, or any other (I've tried that
					 *  and failed).
					 */
					int dist;
					seg = detectCue(cues.get("ConsumableTitle"));
					dist = seg.y1;
					seg = detectCue(cues.get("YesGreen"));
					dist = seg.y1 - dist;
					// distance for the big window should be 262 pixels, for the small one it should be 212.
					
					if (dist > 250) {
						// don't consume the consumable... it's already in use!
						BHBot.log("Error: \"Replace consumable\" dialog detected, meaning consumable is already in use (" + c.getName() + "). Skipping...");
						duplicateConsumables.add(c);
						closePopupSecurely(cues.get("ConsumableTitle"), cues.get("No"));
					} else {
						// consume the consumable:
						closePopupSecurely(cues.get("ConsumableTitle"), cues.get("YesGreen"));
					}
					seg = detectCue(cues.get("StripSelectorButton"), 4 * SECOND); // we do this just in order to wait for the previous menu to reappear
					i.remove();
				}
			}
			
			if (!consumables.isEmpty()) {
				seg = detectCue(cues.get("ScrollerAtBottom"), 500);
				if (seg != null)
					break; // there is nothing we can do anymore... we've scrolled to the bottom and haven't found the icon(s). We obviously don't have the required consumable(s)! 
				
				// lets scroll down:
				seg = detectCue(cues.get("DropDownDown"), 4 * SECOND);
				clickOnSeg(seg);
				
				readScreen(500); // so that the scroller stabilizes a bit
			}
		}
		
		// OK, we're done, lets close the character menu window:
		boolean result = closePopupSecurely(cues.get("StripSelectorButton"), cues.get("X"));
		if (!result) {
			BHBot.log("Done. Error detected while trying to close character window. Ignoring...");
			return;
		}
		
		if (!consumables.isEmpty()) {
			BHBot.log("Some consumables were not found (out of stock?) so were not consumed. These are: " + Misc.listToString(consumables) + ".");
			
			for (ConsumableType c : consumables) {
				BHBot.settings.consumables.remove(c.getName());
			}
			
			BHBot.log("The following consumables have been removed from auto-consume list: " + Misc.listToString(consumables) + ". In order to reactivate them, reload your settings.ini file using 'reload' command.");
		} else {
			if (!duplicateConsumables.isEmpty())
				BHBot.log("Done. Some of the consumables have been skipped since they are already in use: " + Misc.listToString(duplicateConsumables));
			else
				BHBot.log("Done. Desired consumables have been successfully consumed.");
		}
	}
	
	/**
	 * Will make sure all the icons in the inventory have been loaded.
	 */
	private void waitForInventoryIconsToLoad() {
		Bounds bounds = new Bounds(450, 165, 670, 460); // detection area (where inventory icons are visible)
		MarvinSegment seg;
		Cue cue = new Cue(cues.get("LoadingInventoryIcon"), bounds);
		
		int counter = 0;
		seg = detectCue(cue);
		while (seg != null) {
			readScreen(SECOND);
			
			seg = detectCue(cues.get("StripSelectorButton"));
			if (seg == null) {
				BHBot.log("Error: while detecting possible loading of inventory icons, inventory cue has not been detected! Ignoring...");
				return;
			}
			
			seg = detectCue(cue);
			counter++;
			if (counter > 100) {
				BHBot.log("Error: loading of icons has been detected in the inventory screen, but it didn't finish in time. Ignoring...");
				return;
			}
		}
	}
	
	/**
	 * Will reset readout timers.
	 */
	public void resetTimers() {
		timeLastBadgesCheck = 0;
		timeLastEnergyCheck = 0;
		timeLastShardsCheck = 0;
		timeLastTicketsCheck = 0;
		timeLastTokensCheck = 0;
		
		timeLastConsumablesCheck = 0;
	}
	
}
