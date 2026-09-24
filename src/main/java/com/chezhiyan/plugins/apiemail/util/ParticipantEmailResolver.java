package com.chezhiyan.plugins.apiemail.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.joget.apps.app.dao.PackageDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.model.PackageParticipant;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;

public class ParticipantEmailResolver {

    public static final String TYPE_PARTICIPANT = "participant";
    public static final String TYPE_USER = "user";
    public static final String TYPE_GROUP = "group";
    public static final String TYPE_DEPARTMENT = "department";
    public static final String TYPE_VARIABLE = "variable";
    public static final String TYPE_EMAIL = "email";

    public String resolve(
            String recipientsText,
            WorkflowAssignment assignment,
            AppDefinition appDef) {

        Collection<String> emails = resolveToList(recipientsText, assignment, appDef);
        return join(emails);
    }

    public Collection<String> resolveToList(
            String recipientsText,
            WorkflowAssignment assignment,
            AppDefinition appDef) {

        Set<String> result = new LinkedHashSet<String>();

        if (recipientsText == null || recipientsText.trim().isEmpty()) {
            return result;
        }

        String[] entries = recipientsText.split(";");
        for (String rawEntry : entries) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            String type;
            String value;
            int colon = entry.indexOf(':');
            if (colon >= 0) {
                type = entry.substring(0, colon).trim().toLowerCase();
                value = entry.substring(colon + 1).trim();
            } else if (entry.indexOf('@') >= 0) {
                type = TYPE_EMAIL;
                value = entry;
            } else {
                type = null;
                value = entry;
            }

            if (value.isEmpty()) {
                continue;
            }

            resolveType(type, value, assignment, appDef, result);
        }

        return result;
    }

    private void resolveType(
            String type,
            String value,
            WorkflowAssignment assignment,
            AppDefinition appDef,
            Set<String> result) {

        try {
            if (type == null) {
                resolveDefault(value, assignment, appDef, result);

            } else if (TYPE_PARTICIPANT.equals(type)) {
                resolveParticipant(value, assignment, result);

            } else if (TYPE_GROUP.equals(type)) {
                resolveGroup(value, result);

            } else if (TYPE_DEPARTMENT.equals(type)) {
                resolveDepartment(value, result);

            } else if (TYPE_VARIABLE.equals(type)) {
                String processed = AppUtil.processHashVariable(value, assignment, null, null, appDef);
                resolveRaw(processed, result);

            } else if (TYPE_EMAIL.equals(type)) {
                String processed = AppUtil.processHashVariable(value, assignment, null, null, appDef);
                resolveRaw(processed, result);

            } else {
                resolveUser(value, result);
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "API Email Tool: failed to resolve participant (type=" + type + " value=" + value + ")");
        }
    }

    private void resolveDefault(
            String value,
            WorkflowAssignment assignment,
            AppDefinition appDef,
            Set<String> result) {

        boolean resolved = resolveParticipantFromProcess(value, assignment, result);

        if (!resolved) {
            User user = getDirectoryManager().getUserByUsername(value);
            if (user != null && !isBlank(user.getEmail())) {
                result.add(user.getEmail().trim());
                resolved = true;
            }
        }

        if (!resolved && value.indexOf('@') >= 0) {
            String processed = AppUtil.processHashVariable(value, assignment, null, null, appDef);
            resolveRaw(processed, result);
            resolved = true;
        }

        if (!resolved) {
            LogUtil.warn(getClass().getName(),
                    "API Email Tool: participant not found in process or directory: " + value);
        }
    }

    private void resolveParticipant(String participantId, WorkflowAssignment assignment, Set<String> result) {
        resolveParticipantFromProcess(participantId, assignment, result);
    }

    private boolean resolveParticipantFromProcess(
            String participantId,
            WorkflowAssignment current,
            Set<String> result) {

        if (isBlank(participantId)) {
            return false;
        }
        participantId = participantId.trim();

        if (current == null || isBlank(current.getProcessId())) {
            LogUtil.warn(getClass().getName(),
                    "API Email Tool: no workflow assignment context to resolve participant: " + participantId);
            return false;
        }

        if (resolveFromParticipantMapping(participantId, current, result)) {
            return true;
        }

        try {
            WorkflowManager wm = getWorkflowManager();

            WorkflowAssignment direct = wm.getAssignment(participantId);
            if (direct != null) {
                addAssigneeEmails(direct, result);
                return true;
            }

            Collection<WorkflowAssignment> assignments = wm.getAssignmentsByProcessIds(
                    java.util.Collections.singletonList(current.getProcessId()),
                    null, false, 0, -1);

            if (assignments == null) {
                return false;
            }

            for (WorkflowAssignment a : assignments) {
                if (matchesAssignment(a, participantId)) {
                    addAssigneeEmails(a, result);
                    return true;
                }
            }

            StringBuilder sb = new StringBuilder();
            for (WorkflowAssignment a : assignments) {
                sb.append("{").append(a.getActivityId())
                        .append("|def=").append(a.getActivityDefId())
                        .append("|assignee=").append(a.getAssigneeId()).append("} ");
            }
            LogUtil.warn(getClass().getName(),
                    "API Email Tool: no match for participant " + participantId
                    + " in process " + current.getProcessId()
                    + ". assignments: " + sb.toString());
            return false;

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "API Email Tool: failed to resolve participant from process: " + participantId);
            return false;
        }
    }

    private boolean resolveFromParticipantMapping(
            String participantId,
            WorkflowAssignment assignment,
            Set<String> result) {

        try {
            String processDefId = assignment.getProcessDefId();
            if (isBlank(processDefId)) {
                LogUtil.warn(getClass().getName(),
                        "API Email Tool: no process definition id to resolve participant: " + participantId);
                return false;
            }

            String packageId = WorkflowUtil.getProcessDefPackageId(processDefId);
            String processId = assignment.getProcessId();
            String version = assignment.getProcessVersion();
            if (isBlank(version)) {
                version = WorkflowUtil.getProcessDefVersion(processDefId);
            }
            String activityId = assignment.getActivityId();
            String requesterUsername = assignment.getProcessRequesterId();

            List<String> usernames = WorkflowUtil.getAssignmentUsers(
                    packageId, processDefId, processId, version, activityId, requesterUsername, participantId);

            if (usernames != null && !usernames.isEmpty()) {
                for (String username : usernames) {
                    resolveUser(username, result);
                }
                return true;
            }

            return resolveFromPackageParticipantMap(
                    packageId, version, processDefId, participantId, assignment, result);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "API Email Tool: failed to resolve participant mapping: " + participantId);
            return false;
        }
    }

    private boolean resolveFromPackageParticipantMap(
            String packageId,
            String version,
            String processDefId,
            String participantId,
            WorkflowAssignment assignment,
            Set<String> result) {

        try {
            String processDefIdWithoutVersion = WorkflowUtil.getProcessDefIdWithoutVersion(processDefId);

            Object daoBean = AppUtil.getApplicationContext().getBean("packageDefinitionDao");
            if (!(daoBean instanceof PackageDefinitionDao)) {
                LogUtil.warn(getClass().getName(),
                        "API Email Tool: packageDefinitionDao bean not available");
                return false;
            }

            PackageDefinition packageDefinition = null;
            if (!isBlank(version)) {
                packageDefinition = ((PackageDefinitionDao) daoBean)
                        .loadPackageDefinition(packageId, Long.valueOf(version));
            }
            if (packageDefinition == null) {
                LogUtil.warn(getClass().getName(),
                        "API Email Tool: package definition not found for package=" + packageId
                        + " version=" + version);
                return false;
            }

            PackageParticipant participant = packageDefinition.getPackageParticipant(
                    processDefIdWithoutVersion, participantId);
            if (participant == null) {
                LogUtil.warn(getClass().getName(),
                        "API Email Tool: no mapping for participant " + participantId
                        + " (key=" + processDefIdWithoutVersion + "::" + participantId + ")");
                return false;
            }

            String type = participant.getType();
            String value = participant.getValue();

            LogUtil.info(getClass().getName(),
                    "API Email Tool: resolving participant " + participantId
                    + " mapping type=" + type + " value=" + value);

            if ("user".equalsIgnoreCase(type)) {
                boolean any = false;
                for (String part : splitParticipantValues(value)) {
                    if (part.indexOf('@') >= 0) {
                        result.add(part);
                        any = true;
                    } else {
                        int before = result.size();
                        resolveUser(part, result);
                        if (result.size() > before) {
                            any = true;
                        }
                    }
                }
                return any;

            } else if ("group".equalsIgnoreCase(type)) {
                boolean any = false;
                for (String part : splitParticipantValues(value)) {
                    Collection<User> users = getDirectoryManager().getUserByGroupName(part);
                    if (users == null || users.isEmpty()) {
                        users = getDirectoryManager().getUserByGroupId(part);
                    }
                    if (users != null && !users.isEmpty()) {
                        for (User user : users) {
                            if (user != null && !isBlank(user.getEmail())) {
                                result.add(user.getEmail().trim());
                                any = true;
                            }
                        }
                    } else {
                        LogUtil.warn(getClass().getName(),
                                "API Email Tool: group not found for participant mapping: " + part);
                    }
                }
                return any;

            } else if ("requester".equalsIgnoreCase(type)) {
                String requesterUsername = assignment.getProcessRequesterId();
                if (isBlank(requesterUsername) && !isBlank(value)) {
                    try {
                        requesterUsername = getWorkflowManager().getUserByProcessIdAndActivityDefId(
                                processDefId, assignment.getProcessId(), value);
                    } catch (Exception e) {
                        LogUtil.warn(getClass().getName(),
                                "API Email Tool: could not resolve requester via activity: " + e.getMessage());
                    }
                }
                if (isBlank(requesterUsername)) {
                    LogUtil.warn(getClass().getName(),
                            "API Email Tool: no requester found for participant " + participantId);
                    return false;
                }
                resolveUser(requesterUsername.trim(), result);
                return true;

            } else if ("role".equalsIgnoreCase(type)) {
                boolean any = false;
                for (String part : splitParticipantValues(value)) {
                    int before = result.size();
                    resolveUser(part, result);
                    if (result.size() > before) {
                        any = true;
                    }
                }
                return any;

            } else {
                LogUtil.warn(getClass().getName(),
                        "API Email Tool: unsupported participant mapping type: " + type
                        + " for participant " + participantId);
                return false;
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "API Email Tool: failed to resolve participant from package definition: " + participantId);
            return false;
        }
    }

    private String[] splitParticipantValues(String value) {
        if (value == null) {
            return new String[0];
        }
        String[] raw = value.trim().split("[,;]");
        List<String> parts = new ArrayList<String>();
        for (String part : raw) {
            part = part.trim();
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts.toArray(new String[parts.size()]);
    }

    private boolean matchesAssignment(WorkflowAssignment a, String entry) {
        if (equalsIgnoreCase(a.getActivityId(), entry)) {
            return true;
        }
        if (equalsIgnoreCase(a.getActivityDefId(), entry)) {
            return true;
        }
        if (a.getActivityId() != null
                && (a.getActivityId().equalsIgnoreCase(entry)
                || a.getActivityId().toLowerCase().endsWith(entry.toLowerCase())
                || a.getActivityId().toLowerCase().contains(entry.toLowerCase()))) {
            return true;
        }
        if (a.getActivityDefId() != null
                && (a.getActivityDefId().equalsIgnoreCase(entry)
                || a.getActivityDefId().toLowerCase().endsWith(entry.toLowerCase())
                || a.getActivityDefId().toLowerCase().contains(entry.toLowerCase()))) {
            return true;
        }
        return false;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private void addAssigneeEmails(WorkflowAssignment assignment, Set<String> result) {
        if (assignment.getAssigneeId() != null && !assignment.getAssigneeId().trim().isEmpty()) {
            resolveUser(assignment.getAssigneeId().trim(), result);
        }
        if (assignment.getAssigneeList() != null) {
            for (String username : assignment.getAssigneeList()) {
                resolveUser(username, result);
            }
        }
    }

    private void resolveRaw(String text, Set<String> result) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String[] parts = text.split("[;,]");
        for (String part : parts) {
            String email = part.trim();
            if (!email.isEmpty()) {
                result.add(email);
            }
        }
    }

    private void resolveUser(String username, Set<String> result) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        username = username.trim();
        User user = getDirectoryManager().getUserByUsername(username);
        if (user == null) {
            LogUtil.warn(getClass().getName(),
                    "API Email Tool: user not found: " + username);
            return;
        }
        String email = user.getEmail();
        if (email == null || email.trim().isEmpty()) {
            LogUtil.warn(getClass().getName(),
                    "API Email Tool: user has no email: " + username);
            return;
        }
        result.add(email.trim());
    }

    private void resolveGroup(String groupName, Set<String> result) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return;
        }
        Collection<User> users = getDirectoryManager().getUserByGroupName(groupName.trim());
        if (users == null) {
            LogUtil.warn(getClass().getName(),
                    "API Email Tool: group not found: " + groupName);
            return;
        }
        for (User user : users) {
            resolveUser(user.getUsername(), result);
        }
    }

    private void resolveDepartment(String departmentId, Set<String> result) {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return;
        }
        Collection<User> users = getDirectoryManager().getUserByDepartmentId(departmentId.trim());
        if (users == null) {
            LogUtil.warn(getClass().getName(),
                    "API Email Tool: department not found: " + departmentId);
            return;
        }
        for (User user : users) {
            resolveUser(user.getUsername(), result);
        }
    }

    private WorkflowManager getWorkflowManager() {
        return (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
    }

    private DirectoryManager getDirectoryManager() {
        return (DirectoryManager) AppUtil.getApplicationContext().getBean("directoryManager");
    }

    private String join(Collection<String> collection) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }
        List<String> list = new ArrayList<String>(collection);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(";");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}