package gsi.investalia.server.wade;

import java.util.ArrayList;
import java.util.List;

import gsi.investalia.domain.User;
import gsi.investalia.domain.Tag;
import gsi.investalia.json.JSONAdapter;
import gsi.investalia.server.db.HsqldbInterface;
import jade.lang.acl.ACLMessage;

import jade.lang.acl.MessageTemplate;

import com.tilab.wade.performer.layout.TransitionLayout;
import com.tilab.wade.performer.layout.MarkerLayout;
import com.tilab.wade.performer.layout.ActivityLayout;
import com.tilab.wade.performer.layout.WorkflowLayout;
import com.tilab.wade.performer.CodeExecutionBehaviour;
import com.tilab.wade.performer.Transition;
import com.tilab.wade.performer.WorkflowBehaviour;

@WorkflowLayout(transitions = { @TransitionLayout(routingpoints = "(310,146)", to = "WaitForLogin", from = "LoginFailure"), @TransitionLayout(routingpoints = "(117,224)", to = "WaitForLogin", from = "INITIAL") }, entryPoint = @MarkerLayout(position = "(31,203)", activityName = "WaitForLogin"), activities = { @ActivityLayout(position = "(478,252)", name = "LoginSuccessful"), @ActivityLayout(position = "(466,121)", name = "LoginFailure"), @ActivityLayout(size = "(135,50)", position = "(292,173)", name = "CheckLogin"), @ActivityLayout(size = "(122,50)", position = "(118,194)", name = "WaitForLogin") })
public class LoginWorkflow extends WorkflowBehaviour {

	private ACLMessage aclMessage;
	private User loggedUser;
	

	/* Activities for this workflow */
	public static final String WAITFORLOGIN_ACTIVITY = "WaitForLogin";
	public static final String LOGINFAILURE_ACTIVITY = "LoginFailure";
	public static final String CHECKLOGIN_ACTIVITY = "CheckLogin";
	public static final String LOGINSUCCESSFUL_ACTIVITY = "LoginSuccessful";
	public static final String RECOMMENDATION_ACTIVITY = "Recommendation";

	/* Conditions for the transitions */
	public static final String CORRECTLOGIN_CONDITION = "CorrectLogin";
	public static final String WRONGLOGIN_CONDITION = "WrongLogin";
	public static final String MESSAGERECEIVED_CONDITION = "MessageReceived";

	private boolean loginMessage, successfulLogin;

	private void defineActivities() {
		CodeExecutionBehaviour waitForLoginActivity = new CodeExecutionBehaviour(
				WAITFORLOGIN_ACTIVITY, this);
		registerActivity(waitForLoginActivity, INITIAL);
		CodeExecutionBehaviour loginFailureActivity = new CodeExecutionBehaviour(
				LOGINFAILURE_ACTIVITY, this);
		registerActivity(loginFailureActivity);
		CodeExecutionBehaviour checkLoginActivity = new CodeExecutionBehaviour(
				CHECKLOGIN_ACTIVITY, this);
		registerActivity(checkLoginActivity);
		CodeExecutionBehaviour loginSuccessfulActivity = new CodeExecutionBehaviour(
				LOGINSUCCESSFUL_ACTIVITY, this);
		registerActivity(loginSuccessfulActivity);
		CodeExecutionBehaviour recommendationActivity = new CodeExecutionBehaviour(
				RECOMMENDATION_ACTIVITY, this);
		registerActivity(recommendationActivity);

	}

	private void defineTransitions() {
		registerTransition(new Transition(MESSAGERECEIVED_CONDITION, this),
				WAITFORLOGIN_ACTIVITY, CHECKLOGIN_ACTIVITY);
		registerTransition(new Transition(CORRECTLOGIN_CONDITION, this),
				CHECKLOGIN_ACTIVITY, LOGINSUCCESSFUL_ACTIVITY);
		registerTransition(new Transition(), CHECKLOGIN_ACTIVITY,
				LOGINFAILURE_ACTIVITY);
		registerTransition(new Transition(), LOGINFAILURE_ACTIVITY,
				WAITFORLOGIN_ACTIVITY);
		registerTransition(new Transition(MESSAGERECEIVED_CONDITION,this), 
				LOGINSUCCESSFUL_ACTIVITY, RECOMMENDATION_ACTIVITY);
		registerTransition(new Transition(), RECOMMENDATION_ACTIVITY,
				WAITFORLOGIN_ACTIVITY);

	}

	protected void executeWaitForLogin() throws Exception {
		aclMessage = myAgent.blockingReceive();
		if (aclMessage != null && aclMessage.getPerformative() == ACLMessage.CFP) {
			/* We have received the login message */
			loginMessage = true;
		}

	}

	protected void executeCheckLogin() throws Exception {
	
		// Json to User
		String content = aclMessage.getContent();
		User loginAttemptUser = JSONAdapter.JSONToUser(content);

		// TODO change to mysql
		loggedUser = HsqldbInterface.getUser(loginAttemptUser.getUserName(), 
			loginAttemptUser.getPassword());
		
		// Condition
		successfulLogin = loggedUser != null;
	}
	
	protected void executeRecommendation() throws Exception {
		//TODO : Update the database with new recommendations , or something similar

	}

	protected void executeLoginFailure() throws Exception {
		System.out.println("Login failure");
		ACLMessage loginFailure = aclMessage.createReply();
		loginFailure.setPerformative(ACLMessage.REJECT_PROPOSAL);
		myAgent.send(loginFailure);
	}
	
	protected void executeLoginSuccessful() throws Exception {
		System.out.println("Login successful");
		
		// Log
		System.out.println("user: " + loggedUser.getUserName());
		System.out.println("password: " + loggedUser.getPassword());
		
		ACLMessage loginSuccessful = aclMessage.createReply();
		loginSuccessful.setPerformative(ACLMessage.INFORM);
		loginSuccessful.setContent(JSONAdapter.userToJSON(loggedUser).toString());
		myAgent.send(loginSuccessful);
	}

	protected boolean checkMessageReceived() throws Exception{
		return loginMessage;
	}

	protected boolean checkCorrectLogin() throws Exception{
		return successfulLogin;
	}
	
	protected boolean checkWrongLogin() throws Exception{
		return !successfulLogin;
	}
}
