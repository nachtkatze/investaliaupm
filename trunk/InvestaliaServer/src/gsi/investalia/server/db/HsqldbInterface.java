package gsi.investalia.server.db;

import gsi.investalia.domain.Message;
import gsi.investalia.domain.Tag;
import gsi.investalia.domain.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and gets all the needed information of the database. It works with the
 * domain classes.
 * 
 * @author luis
 */
public class HsqldbInterface {
	private static Connection con;
	private static Statement stmt;

	/**
	 * Connects to the database
	 */
	private static void connectToDatabase() {
		try {
			Class.forName("org.hsqldb.jdbcDriver");
			con = DriverManager.getConnection(
					"jdbc:hsqldb:hsql://localhost/investalia", "sa", "");
			stmt = con.createStatement();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Saves a new message
	 */
	public static void saveMessage(Message message) {
		connectToDatabase();

		// Save the message itself
		String query = "INSERT INTO messages VALUES (Null, ?, ?, ?, ?, ?, ?)";
		PreparedStatement prep;
		try {
			prep = con.prepareStatement(query);
			prep.setInt(1, getUser(message.getUserName()).getId());
			prep.setString(2, message.getTitle());
			prep.setString(3, message.getText());
			prep.setDate(4, (java.sql.Date) message.getDate());
			prep.setInt(5, 0); // Rating = 0 at start
			prep.setInt(6, 0); // Not still read
			prep.executeUpdate();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		// Set the id generated by the db
		setMessageId(message);

		// Save the tags
		query = "INSERT INTO messages_tags VALUES (Null, ?, ?)";
		try {
			prep = con.prepareStatement(query);
			for (Tag tag : message.getTags()) {
				prep.setInt(1, message.getId());
				prep.setInt(2, tag.getId());
				prep.executeUpdate();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Updates the read and liked properties for a message list and user
	 */
	public static void updateReadAndLiked(List<Message> messages, int idUser) {
		for (Message m : messages) {
			updateReadAndLiked(m, idUser);
		}
	}

	/**
	 * Updates the read and liked properties for a message and user
	 */
	public static void updateReadAndLiked(Message m, int idUser) {
		connectToDatabase();

		Message oldMessage = getMessage(m, idUser);
		// If the "new message" is not read, the method does nothing
		if (!m.isRead()) {
			return;
		}
		// New and old message read
		if (oldMessage.isRead()) {
			try {
				// If liked does not change, the method does nothing
				if (m.isLiked() == oldMessage.isLiked()) {
					return;
				}
				// Liked has changed, update the db
				stmt
						.executeQuery("UPDATE read SET liked = true WHERE idmessage = "
								+ m.getId() + " AND idUser = " + idUser);
				// Old disliked and new likes: rating++
				if (m.isLiked()) {
					stmt
							.executeQuery("UPDATE messages SET rating = (rating + 1) WHERE idMessage = "
									+ m.getId());
				}
				// Old liked and new dislikes: rating--
				else {
					stmt
							.executeQuery("UPDATE messages SET rating = (rating - 1) WHERE idMessage = "
									+ m.getId());
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			try {
				// Increase timesRead
				stmt
						.executeQuery("UPDATE messages SET times_read = (times_read + 1) WHERE idMessage = "
								+ m.getId());
				// Add row in read table
				stmt.executeQuery("INSERT INTO read VALUES (Null, " + m.getId()
						+ ", " + idUser + ", " + m.isLiked() + ")");
				// If liked, rating++
				if (m.isLiked()) {
					stmt
							.executeQuery("UPDATE messages SET rating = (rating + 1) WHERE idMessage = "
									+ m.getId());
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Gets a user by its userName
	 */
	public static User getUser(String userName) {
		return getUserFromQuery("SELECT * FROM users WHERE userName = '"
				+ userName + "'");
	}

	/**
	 * Gets a user by its id
	 */
	public static User getUser(int idUser) {
		return getUserFromQuery("SELECT * FROM users WHERE idUser = " + idUser);
	}

	/**
	 * Gets a user by its userName and password
	 */
	public static User getUser(String userName, String password) {
		return getUserFromQuery("SELECT * FROM users WHERE userName = '"
				+ userName + "' AND password = '" + password + "'");
	}

	public static boolean saveNewUser(User user) {
		// Check the username is not already used
		User alreadyRegisteredUser = getUser(user.getUserName());
		if (alreadyRegisteredUser != null) {
			System.out.println("Already registered user");
			return false;
		}

		// Save the user
		String query = "INSERT INTO users values (Null, ?, ?, ?, ?, ?)";
		PreparedStatement prep;
		try {
			prep = con.prepareStatement(query);
			prep.setString(1, user.getUserName());
			prep.setString(2, user.getPassword());
			prep.setString(3, user.getName());
			prep.setString(4, user.getLocation());
			prep.setString(5, user.getEmail());
			prep.executeUpdate();
			return true;
		} catch (SQLException e) {
			System.out.println("SQL exception inserting new user");
			return false;
		}
	}

	/**
	 * Gets the list of all the messages that a user is following
	 */
	public static List<Message> getAllUserMessages(String userName) {
		int idUser = getUser(userName).getId();
		return getMessagesFromQuery("SELECT * FROM messages WHERE idUser = "
				+ idUser + idUser, idUser);
	}

	/**
	 * Gets the list of the messages that a user is following after one given
	 */
	public static List<Message> getUserMessagesSinceLast(String userName,
			int idMessageLast) {
		int idUser = getUser(userName).getId();
		String query = "SELECT * FROM messages WHERE idUser = " + idUser
				+ " AND idMessage > " + idMessageLast;
		return getMessagesFromQuery(query, idUser);
	}

	/**
	 * Gets a message with the read and liked attributes for e given user
	 */
	private static Message getMessage(Message m, int idUser) {
		connectToDatabase();

		try {
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM messages WHERE idMessage = "
							+ m.getId());
			if (rs.next()) {
				return getMessageFromRS(rs, idUser);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static List<Message> getMessagesFromQuery(String query, int idUser) {
		connectToDatabase();
		List<Message> messages = new ArrayList<Message>();
		try {
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				messages.add(getMessageFromRS(rs, idUser));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return messages;
	}

	private static List<Tag> getTagListFromQuery(String query) {
		connectToDatabase();
		List<Tag> tags = new ArrayList<Tag>();
		try {
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				tags.add(new Tag(rs.getInt(1), rs.getString(2)));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return tags;
	}

	private static User getUserFromQuery(String query) {
		connectToDatabase();

		try {
			ResultSet rs = stmt.executeQuery(query);
			if (rs.next()) {
				return new User(rs.getInt(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6),
						getTagsFollowing(rs.getInt(1)), 0 /* irrelevant */);
				// Constructor:
				// id,userName,password,name,location,email,tagsFollowing,lastUpdate
				// Db: IDUSER,USERNAME,PASSWORD,NAME,LOCATION,EMAIL
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static List<Tag> getTagsFollowing(int idUser) {
		return getTagListFromQuery("SELECT idtag FROM users_tags WHERE idUser = "
				+ idUser);
	}

	private static List<Tag> getMessageTags(int idMessage) {
		return getTagListFromQuery("SELECT idtag FROM messages_tags WHERE idMessage = "
				+ idMessage);
	}

	private static void setMessageId(Message message) {
		String query = "SELECT idmessage FROM messages WHERE iduser = ? AND message = ? ORDER BY idmessage DESC LIMIT 1";
		try {
			PreparedStatement prep = con.prepareStatement(query);
			prep.setInt(1, getUser(message.getUserName()).getId());
			prep.setString(2, message.getText());
			ResultSet rs = prep.executeQuery();
			while (rs.next()) {
				message.setId(rs.getInt(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Checks and sets the read and liked attribute for a given message and user
	 */
	private static void setReadAndLiked(Message m, int idUser) {
		connectToDatabase();

		try {
			ResultSet rs = stmt
					.executeQuery("SELECT liked FROM read WHERE iduser = "
							+ idUser + " AND idmessage = " + m.getId());
			if (rs.next()) {
				m.setRead(true);
				m.setLiked(rs.getBoolean(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static Message getMessageFromRS(ResultSet rs, int idUser) {
		// Create the message; read and liked false by default
		Message m;
		try {
			m = new Message(rs.getInt(1), getUser(rs.getInt(2)).getUserName(),
					rs.getString(3), rs.getString(4), getMessageTags(rs
							.getInt(1)), rs.getDate(5), false, false, rs
							.getInt(6), rs.getInt(7));
		} catch (SQLException e) {
			System.out.println("SQL Exception");
			return null;
		}
		// Set the read and liked properties
		setReadAndLiked(m, idUser);
		return m;
		// Constructor:
		// id,userName,title,text,tags,date,read,liked,rating,timesRead
		// Db: IDMESSAGE,IDUSER,TITLE,TEXT,DATE,RATING,TIMES_READ
	}
}
