package eu.siacs.conversations.entities;

import android.annotation.SuppressLint;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.JidHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.pep.Avatar;
import rocks.xmpp.addr.Jid;

@SuppressLint("DefaultLocale")
public class MucOptions {

	private boolean mAutoPushConfiguration = true;

	public Account getAccount() {
		return this.conversation.getAccount();
	}

	public void setSelf(User user) {
		this.self = user;
	}

	public void changeAffiliation(Jid jid, Affiliation affiliation) {
		User user = findUserByRealJid(jid);
		synchronized (users) {
			if (user != null && user.getRole() == Role.NONE) {
				users.remove(user);
				if (affiliation.ranks(Affiliation.MEMBER)) {
					user.affiliation = affiliation;
					users.add(user);
				}
			}
		}
	}

	public void flagNoAutoPushConfiguration() {
		mAutoPushConfiguration = false;
	}

	public boolean autoPushConfiguration() {
		return mAutoPushConfiguration;
	}

	public boolean isSelf(Jid counterpart) {
		return counterpart.equals(self.getFullJid());
	}

	public void resetChatState() {
		synchronized (users) {
			for (User user : users) {
				user.chatState = Config.DEFAULT_CHATSTATE;
			}
		}
	}

	public enum Affiliation {
		OWNER("owner", 4, R.string.owner),
		ADMIN("admin", 3, R.string.admin),
		MEMBER("member", 2, R.string.member),
		OUTCAST("outcast", 0, R.string.outcast),
		NONE("none", 1, R.string.no_affiliation);

		Affiliation(String string, int rank, int resId) {
			this.string = string;
			this.resId = resId;
			this.rank = rank;
		}

		private String string;
		private int resId;
		private int rank;

		public int getResId() {
			return resId;
		}

		@Override
		public String toString() {
			return this.string;
		}

		public boolean outranks(Affiliation affiliation) {
			return rank > affiliation.rank;
		}

		public boolean ranks(Affiliation affiliation) {
			return rank >= affiliation.rank;
		}
	}

	public enum Role {
		MODERATOR("moderator", R.string.moderator, 3),
		VISITOR("visitor", R.string.visitor, 1),
		PARTICIPANT("participant", R.string.participant, 2),
		NONE("none", R.string.no_role, 0);

		Role(String string, int resId, int rank) {
			this.string = string;
			this.resId = resId;
			this.rank = rank;
		}

		private String string;
		private int resId;
		private int rank;

		public int getResId() {
			return resId;
		}

		@Override
		public String toString() {
			return this.string;
		}

		public boolean ranks(Role role) {
			return rank >= role.rank;
		}
	}

	public enum Error {
		NO_RESPONSE,
		SERVER_NOT_FOUND,
		NONE,
		NICK_IN_USE,
		PASSWORD_REQUIRED,
		BANNED,
		MEMBERS_ONLY,
		KICKED,
		SHUTDOWN,
		INVALID_NICK,
		UNKNOWN
	}

	public static final String STATUS_CODE_SELF_PRESENCE = "110";
	public static final String STATUS_CODE_ROOM_CREATED = "201";
	public static final String STATUS_CODE_BANNED = "301";
	public static final String STATUS_CODE_CHANGED_NICK = "303";
	public static final String STATUS_CODE_KICKED = "307";
	public static final String STATUS_CODE_AFFILIATION_CHANGE = "321";
	public static final String STATUS_CODE_LOST_MEMBERSHIP = "322";
	public static final String STATUS_CODE_SHUTDOWN = "332";

	private interface OnEventListener {
		void onSuccess();

		void onFailure();
	}

	public interface OnRenameListener extends OnEventListener {

	}

	public static class User implements Comparable<User> {
		private Role role = Role.NONE;
		private Affiliation affiliation = Affiliation.NONE;
		private Jid realJid;
		private Jid fullJid;
		private long pgpKeyId = 0;
		private Avatar avatar;
		private MucOptions options;
		private ChatState chatState = Config.DEFAULT_CHATSTATE;

		public User(MucOptions options, Jid from) {
			this.options = options;
			this.fullJid = from;
		}

		public String getName() {
			return fullJid == null ? null : fullJid.getResource();
		}

		public void setRealJid(Jid jid) {
			this.realJid = jid != null ? jid.asBareJid() : null;
		}

		public Role getRole() {
			return this.role;
		}

		public void setRole(String role) {
			if (role == null) {
				this.role = Role.NONE;
				return;
			}
			role = role.toLowerCase();
			switch (role) {
				case "moderator":
					this.role = Role.MODERATOR;
					break;
				case "participant":
					this.role = Role.PARTICIPANT;
					break;
				case "visitor":
					this.role = Role.VISITOR;
					break;
				default:
					this.role = Role.NONE;
					break;
			}
		}

		public Affiliation getAffiliation() {
			return this.affiliation;
		}

		public void setAffiliation(String affiliation) {
			if (affiliation == null) {
				this.affiliation = Affiliation.NONE;
				return;
			}
			affiliation = affiliation.toLowerCase();
			switch (affiliation) {
				case "admin":
					this.affiliation = Affiliation.ADMIN;
					break;
				case "owner":
					this.affiliation = Affiliation.OWNER;
					break;
				case "member":
					this.affiliation = Affiliation.MEMBER;
					break;
				case "outcast":
					this.affiliation = Affiliation.OUTCAST;
					break;
				default:
					this.affiliation = Affiliation.NONE;
			}
		}

		public void setPgpKeyId(long id) {
			this.pgpKeyId = id;
		}

		public long getPgpKeyId() {
			if (this.pgpKeyId != 0) {
				return this.pgpKeyId;
			} else if (realJid != null) {
				return getAccount().getRoster().getContact(realJid).getPgpKeyId();
			} else {
				return 0;
			}
		}

		public Contact getContact() {
			if (fullJid != null) {
				return getAccount().getRoster().getContactFromRoster(realJid);
			} else if (realJid != null) {
				return getAccount().getRoster().getContact(realJid);
			} else {
				return null;
			}
		}

		public boolean setAvatar(Avatar avatar) {
			if (this.avatar != null && this.avatar.equals(avatar)) {
				return false;
			} else {
				this.avatar = avatar;
				return true;
			}
		}

		public String getAvatar() {
			return avatar == null ? null : avatar.getFilename();
		}

		public Account getAccount() {
			return options.getAccount();
		}

		public Conversation getConversation() {
			return options.getConversation();
		}

		public Jid getFullJid() {
			return fullJid;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			User user = (User) o;

			if (role != user.role) return false;
			if (affiliation != user.affiliation) return false;
			if (realJid != null ? !realJid.equals(user.realJid) : user.realJid != null)
				return false;
			return fullJid != null ? fullJid.equals(user.fullJid) : user.fullJid == null;

		}

		@Override
		public int hashCode() {
			int result = role != null ? role.hashCode() : 0;
			result = 31 * result + (affiliation != null ? affiliation.hashCode() : 0);
			result = 31 * result + (realJid != null ? realJid.hashCode() : 0);
			result = 31 * result + (fullJid != null ? fullJid.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return "[fulljid:" + String.valueOf(fullJid) + ",realjid:" + String.valueOf(realJid) + ",affiliation" + affiliation.toString() + "]";
		}

		public boolean realJidMatchesAccount() {
			return realJid != null && realJid.equals(options.account.getJid().asBareJid());
		}

		@Override
		public int compareTo(User another) {
			if (another.getAffiliation().outranks(getAffiliation())) {
				return 1;
			} else if (getAffiliation().outranks(another.getAffiliation())) {
				return -1;
			} else {
				return getComparableName().compareToIgnoreCase(another.getComparableName());
			}
		}


		private String getComparableName() {
			Contact contact = getContact();
			if (contact != null) {
				return contact.getDisplayName();
			} else {
				String name = getName();
				return name == null ? "" : name;
			}
		}

		public Jid getRealJid() {
			return realJid;
		}

		public boolean setChatState(ChatState chatState) {
			if (this.chatState == chatState) {
				return false;
			}
			this.chatState = chatState;
			return true;
		}
	}

	private Account account;
	private final Set<User> users = new HashSet<>();
	private final List<String> features = new ArrayList<>();
	private Data form = new Data();
	private final Conversation conversation;
	private boolean isOnline = false;
	private Error error = Error.NONE;
	public OnRenameListener onRenameListener = null;
	private User self;
	private String password = null;

	public MucOptions(Conversation conversation) {
		this.account = conversation.getAccount();
		this.conversation = conversation;
		this.self = new User(this, createJoinJid(getProposedNick()));
	}

	public boolean updateConfiguration(List<String> features, String name, Data data) {
		updateFeatures(features);
		updateFormData(data == null ? new Data() : data);
		Field allowPmField = this.form.getFieldByName("muc#roomconfig_allowpm");
		boolean changed = false;
		changed |= conversation.setAttribute(Conversation.ATTRIBUTE_ALLOW_PM, allowPmField == null || "1".equals(allowPmField.getValue()));
		changed |= conversation.setAttribute(Conversation.ATTRIBUTE_MEMBERS_ONLY, this.hasFeature("muc_membersonly"));
		changed |= conversation.setAttribute(Conversation.ATTRIBUTE_MODERATED, this.hasFeature("muc_moderated"));
		changed |= conversation.setAttribute(Conversation.ATTRIBUTE_NON_ANONYMOUS, this.hasFeature("muc_nonanonymous"));
		changed |= setName(name);
		return changed;
	}

	private void updateFeatures(List<String> features) {
		this.features.clear();
		this.features.addAll(features);
	}

	public String getAvatar() {
		return account.getRoster().getContact(conversation.getJid()).getAvatar();
	}

	private void updateFormData(Data form) {
		this.form = form;
	}

	public boolean hasFeature(String feature) {
		return this.features.contains(feature);
	}

	public boolean hasVCards() {
	    return hasFeature("vcard-temp");
    }

	public boolean canInvite() {
		Field field = this.form.getFieldByName("muc#roomconfig_allowinvites");
		return !membersOnly() || self.getRole().ranks(Role.MODERATOR) || (field != null && "1".equals(field.getValue()));
	}

	public boolean canChangeSubject() {
		Field field = this.form.getFieldByName("muc#roomconfig_changesubject");
		return self.getRole().ranks(Role.MODERATOR) || (field != null && "1".equals(field.getValue()));
	}

	public boolean allowPm() {
		return conversation.getBooleanAttribute(Conversation.ATTRIBUTE_ALLOW_PM, false);
	}

	public boolean participating() {
		return !online()
				|| self.getRole().ranks(Role.PARTICIPANT)
				|| hasFeature("muc_unmoderated");
	}

	public boolean membersOnly() {
		return conversation.getBooleanAttribute(Conversation.ATTRIBUTE_MEMBERS_ONLY, false);
	}

	public boolean mamSupport() {
		return hasFeature(Namespace.MAM) || hasFeature(Namespace.MAM_LEGACY);
	}

	public boolean mamLegacy() {
		return hasFeature(Namespace.MAM_LEGACY) && !hasFeature(Namespace.MAM);
	}

	public boolean nonanonymous() {
		return conversation.getBooleanAttribute(Conversation.ATTRIBUTE_NON_ANONYMOUS, false);
	}

	public boolean isPrivateAndNonAnonymous() {
		return membersOnly() && nonanonymous();
	}

	public boolean moderated() {
		return conversation.getBooleanAttribute(Conversation.ATTRIBUTE_MODERATED, false);
	}

	public User deleteUser(Jid jid) {
		User user = findUserByFullJid(jid);
		if (user != null) {
			synchronized (users) {
				users.remove(user);
				boolean realJidInMuc = false;
				for (User u : users) {
					if (user.realJid != null && user.realJid.equals(u.realJid)) {
						realJidInMuc = true;
						break;
					}
				}
				boolean self = user.realJid != null && user.realJid.equals(account.getJid().asBareJid());
				if (membersOnly()
						&& nonanonymous()
						&& user.affiliation.ranks(Affiliation.MEMBER)
						&& user.realJid != null
						&& !realJidInMuc
						&& !self) {
					user.role = Role.NONE;
					user.avatar = null;
					user.fullJid = null;
					users.add(user);
				}
			}
		}
		return user;
	}

	//returns true if real jid was new;
	public boolean updateUser(User user) {
		User old;
		boolean realJidFound = false;
		if (user.fullJid == null && user.realJid != null) {
			old = findUserByRealJid(user.realJid);
			realJidFound = old != null;
			if (old != null) {
				if (old.fullJid != null) {
					return false; //don't add. user already exists
				} else {
					synchronized (users) {
						users.remove(old);
					}
				}
			}
		} else if (user.realJid != null) {
			old = findUserByRealJid(user.realJid);
			realJidFound = old != null;
			synchronized (users) {
				if (old != null && old.fullJid == null) {
					users.remove(old);
				}
			}
		}
		old = findUserByFullJid(user.getFullJid());
		synchronized (this.users) {
			if (old != null) {
				users.remove(old);
			}
			boolean fullJidIsSelf = isOnline && user.getFullJid() != null && user.getFullJid().equals(self.getFullJid());
			if ((!membersOnly() || user.getAffiliation().ranks(Affiliation.MEMBER))
					&& user.getAffiliation().outranks(Affiliation.OUTCAST)
					&& !fullJidIsSelf) {
				this.users.add(user);
				return !realJidFound && user.realJid != null;
			}
		}
		return false;
	}

	public User findUserByFullJid(Jid jid) {
		if (jid == null) {
			return null;
		}
		synchronized (users) {
			for (User user : users) {
				if (jid.equals(user.getFullJid())) {
					return user;
				}
			}
		}
		return null;
	}

	public User findUserByRealJid(Jid jid) {
		if (jid == null) {
			return null;
		}
		synchronized (users) {
			for (User user : users) {
				if (jid.equals(user.realJid)) {
					return user;
				}
			}
		}
		return null;
	}

	public User findUser(ReadByMarker readByMarker) {
		if (readByMarker.getRealJid() != null) {
			User user = findUserByRealJid(readByMarker.getRealJid().asBareJid());
			if (user == null) {
				user = new User(this, readByMarker.getFullJid());
				user.setRealJid(readByMarker.getRealJid());
			}
			return user;
		} else if (readByMarker.getFullJid() != null) {
			return findUserByFullJid(readByMarker.getFullJid());
		} else {
			return null;
		}
	}

	public boolean isContactInRoom(Contact contact) {
		return findUserByRealJid(contact.getJid().asBareJid()) != null;
	}

	public boolean isUserInRoom(Jid jid) {
		return findUserByFullJid(jid) != null;
	}

	public void setError(Error error) {
		this.isOnline = isOnline && error == Error.NONE;
		this.error = error;
	}

	public boolean setOnline() {
		boolean before = this.isOnline;
		this.isOnline = true;
		return !before;
	}

	public ArrayList<User> getUsers() {
		return getUsers(true);
	}

	public ArrayList<User> getUsers(boolean includeOffline) {
		synchronized (users) {
			if (includeOffline) {
				return new ArrayList<>(users);
			} else {
				ArrayList<User> onlineUsers = new ArrayList<>();
				for (User user : users) {
					if (user.getRole().ranks(Role.PARTICIPANT)) {
						onlineUsers.add(user);
					}
				}
				return onlineUsers;
			}
		}
	}

	public ArrayList<User> getUsersWithChatState(ChatState state, int max) {
		synchronized (users) {
			ArrayList<User> list = new ArrayList<>();
			for (User user : users) {
				if (user.chatState == state) {
					list.add(user);
					if (list.size() >= max) {
						break;
					}
				}
			}
			return list;
		}
	}

	public List<User> getUsers(int max) {
		ArrayList<User> subset = new ArrayList<>();
		HashSet<Jid> jids = new HashSet<>();
		jids.add(account.getJid().asBareJid());
		synchronized (users) {
			for (User user : users) {
				if (user.getRealJid() == null || jids.add(user.getRealJid())) {
					subset.add(user);
				}
				if (subset.size() >= max) {
					break;
				}
			}
		}
		return subset;
	}

	public int getUserCount() {
		synchronized (users) {
			return users.size();
		}
	}

	private String getProposedNick() {
		if (conversation.getBookmark() != null
				&& conversation.getBookmark().getNick() != null
				&& !conversation.getBookmark().getNick().trim().isEmpty()) {
			return conversation.getBookmark().getNick().trim();
		} else if (!conversation.getJid().isBareJid()) {
			return conversation.getJid().getResource();
		} else {
			return JidHelper.localPartOrFallback(account.getJid());
		}
	}

	public String getActualNick() {
		if (this.self.getName() != null) {
			return this.self.getName();
		} else {
			return this.getProposedNick();
		}
	}

	public boolean online() {
		return this.isOnline;
	}

	public Error getError() {
		return this.error;
	}

	public void setOnRenameListener(OnRenameListener listener) {
		this.onRenameListener = listener;
	}

	public void setOffline() {
		synchronized (users) {
			this.users.clear();
		}
		this.error = Error.NO_RESPONSE;
		this.isOnline = false;
	}

	public User getSelf() {
		return self;
	}

	public boolean setSubject(String subject) {
		return this.conversation.setAttribute("subject", subject);
	}

	public String getSubject() {
		return this.conversation.getAttribute("subject");
	}

	private boolean setName(String name) {
		return this.conversation.setAttribute("muc_name", name);
	}

	public String getName() {
		return this.conversation.getAttribute("muc_name");
	}

	public List<User> getFallbackUsersFromCryptoTargets() {
		List<User> users = new ArrayList<>();
		for (Jid jid : conversation.getAcceptedCryptoTargets()) {
			User user = new User(this, null);
			user.setRealJid(jid);
			users.add(user);
		}
		return users;
	}

	public List<User> getUsersRelevantForNameAndAvatar() {
		final List<User> users;
		if (isOnline) {
			users = getUsers(5);
		} else {
			users = getFallbackUsersFromCryptoTargets();
		}
		return users;
	}

	public String createNameFromParticipants() {
		List<User> users = getUsersRelevantForNameAndAvatar();
		if (users.size() >= 2) {
			StringBuilder builder = new StringBuilder();
			for (User user : users) {
				if (builder.length() != 0) {
					builder.append(", ");
				}
				String name = UIHelper.getDisplayName(user);
				if (name != null) {
					builder.append(name.split("\\s+")[0]);
				}
			}
			return builder.toString();
		} else {
			return null;
		}
	}

	public long[] getPgpKeyIds() {
		List<Long> ids = new ArrayList<>();
		for (User user : this.users) {
			if (user.getPgpKeyId() != 0) {
				ids.add(user.getPgpKeyId());
			}
		}
		ids.add(account.getPgpId());
		long[] primitiveLongArray = new long[ids.size()];
		for (int i = 0; i < ids.size(); ++i) {
			primitiveLongArray[i] = ids.get(i);
		}
		return primitiveLongArray;
	}

	public boolean pgpKeysInUse() {
		synchronized (users) {
			for (User user : users) {
				if (user.getPgpKeyId() != 0) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean everybodyHasKeys() {
		synchronized (users) {
			for (User user : users) {
				if (user.getPgpKeyId() == 0) {
					return false;
				}
			}
		}
		return true;
	}

	public Jid createJoinJid(String nick) {
		try {
			return Jid.of(this.conversation.getJid().asBareJid().toString() + "/" + nick);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	public Jid getTrueCounterpart(Jid jid) {
		if (jid.equals(getSelf().getFullJid())) {
			return account.getJid().asBareJid();
		}
		User user = findUserByFullJid(jid);
		return user == null ? null : user.realJid;
	}

	public String getPassword() {
		this.password = conversation.getAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD);
		if (this.password == null && conversation.getBookmark() != null
				&& conversation.getBookmark().getPassword() != null) {
			return conversation.getBookmark().getPassword();
		} else {
			return this.password;
		}
	}

	public void setPassword(String password) {
		if (conversation.getBookmark() != null) {
			conversation.getBookmark().setPassword(password);
		} else {
			this.password = password;
		}
		conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
	}

	public Conversation getConversation() {
		return this.conversation;
	}

	public List<Jid> getMembers() {
		ArrayList<Jid> members = new ArrayList<>();
		synchronized (users) {
			for (User user : users) {
				if (user.affiliation.ranks(Affiliation.MEMBER) && user.realJid != null) {
					members.add(user.realJid);
				}
			}
		}
		return members;
	}
}
