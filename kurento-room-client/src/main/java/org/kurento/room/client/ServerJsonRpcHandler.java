/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License (LGPL)
 * version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package org.kurento.room.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.kurento.client.IceCandidate;
import org.kurento.jsonrpc.DefaultJsonRpcHandler;
import org.kurento.jsonrpc.Transaction;
import org.kurento.jsonrpc.message.Request;
import org.kurento.room.client.internal.IceCandidateInfo;
import org.kurento.room.client.internal.JsonRoomUtils;
import org.kurento.room.client.internal.MediaErrorInfo;
import org.kurento.room.client.internal.Notification;
import org.kurento.room.client.internal.ParticipantEvictedInfo;
import org.kurento.room.client.internal.ParticipantJoinedInfo;
import org.kurento.room.client.internal.ParticipantLeftInfo;
import org.kurento.room.client.internal.ParticipantPublishedInfo;
import org.kurento.room.client.internal.ParticipantUnpublishedInfo;
import org.kurento.room.client.internal.RoomClosedInfo;
import org.kurento.room.client.internal.SendMessageInfo;
import org.kurento.room.internal.ProtocolElements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Service that handles server JSON-RPC events.
 *
 * @author <a href="mailto:rvlad@naevatec.com">Radu Tom Vlad</a>
 */
public class ServerJsonRpcHandler extends DefaultJsonRpcHandler<JsonObject> {

  private static final Logger log = LoggerFactory.getLogger(ServerJsonRpcHandler.class);

  private BlockingQueue<Notification> notifications = new ArrayBlockingQueue<Notification>(100);

  @Override
  public void handleRequest(Transaction transaction, Request<JsonObject> request) throws Exception {
    Notification notif = null;
    try {
      switch (request.getMethod()) {
        case ProtocolElements.ICECANDIDATE_METHOD :
          notif = iceCandidate(transaction, request);
          break;
        case ProtocolElements.MEDIAERROR_METHOD :
          notif = mediaError(transaction, request);
          break;
        case ProtocolElements.PARTICIPANTJOINED_METHOD :
          notif = participantJoined(transaction, request);
          break;
        case ProtocolElements.PARTICIPANTLEFT_METHOD :
          notif = participantLeft(transaction, request);
          break;
        case ProtocolElements.PARTICIPANTEVICTED_METHOD :
          notif = participantEvicted(transaction, request);
          break;
        case ProtocolElements.PARTICIPANTPUBLISHED_METHOD :
          notif = participantPublished(transaction, request);
          break;
        case ProtocolElements.PARTICIPANTUNPUBLISHED_METHOD :
          notif = participantUnpublished(transaction, request);
          break;
        case ProtocolElements.ROOMCLOSED_METHOD :
          notif = roomClosed(transaction, request);
          break;
        case ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD :
          notif = participantSendMessage(transaction, request);
          break;
        default :
          log.error("Unrecognized request {}", request);
          break;
      }
    } catch (Exception e) {
      log.error("Exception processing request {}", request, e);
      transaction.sendError(e);
    }
    try {
      notifications.put(notif);
      log.debug("Enqueued notification {}", notif);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private Notification participantSendMessage(Transaction transaction, Request<JsonObject> request) {
    String room = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.PARTICIPANTSENDMESSAGE_ROOM_PARAM, String.class);
    String user = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.PARTICIPANTSENDMESSAGE_USER_PARAM, String.class);
    String message = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.PARTICIPANTSENDMESSAGE_MESSAGE_PARAM, String.class);
    SendMessageInfo eventInfo = new SendMessageInfo(room, user, message);
    log.debug("Recvd send message event {}", eventInfo);
    return eventInfo;
  }

  private Notification roomClosed(Transaction transaction, Request<JsonObject> request) {
    String room = JsonRoomUtils.getRequestParam(request, ProtocolElements.ROOMCLOSED_ROOM_PARAM,
        String.class);
    RoomClosedInfo eventInfo = new RoomClosedInfo(room);
    log.debug("Recvd room closed event {}", eventInfo);
    return eventInfo;
  }

  private Notification participantUnpublished(Transaction transaction, Request<JsonObject> request) {
    String name = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.PARTICIPANTUNPUBLISHED_NAME_PARAM, String.class);
    ParticipantUnpublishedInfo eventInfo = new ParticipantUnpublishedInfo(name);
    log.debug("Recvd participant unpublished event {}", eventInfo);
    return eventInfo;
  }

  private Notification participantPublished(Transaction transaction, Request<JsonObject> request) {
    String id = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.PARTICIPANTPUBLISHED_USER_PARAM, String.class);
    JsonArray jsonStreams = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.PARTICIPANTPUBLISHED_STREAMS_PARAM, JsonArray.class);
    Iterator<JsonElement> streamIt = jsonStreams.iterator();
    List<String> streams = new ArrayList<String>();
    while (streamIt.hasNext()) {
      streams.add(JsonRoomUtils.getResponseProperty(streamIt.next(),
          ProtocolElements.PARTICIPANTPUBLISHED_STREAMID_PARAM, String.class));
    }
    ParticipantPublishedInfo eventInfo = new ParticipantPublishedInfo(id, streams);
    log.debug("Recvd published event {}", eventInfo);
    return eventInfo;
  }

  private Notification participantEvicted(Transaction transaction, Request<JsonObject> request) {
    ParticipantEvictedInfo eventInfo = new ParticipantEvictedInfo();
    log.debug("Recvd participant evicted event {}", eventInfo);
    return eventInfo;
  }

  private Notification participantLeft(Transaction transaction, Request<JsonObject> request) {
    String name = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.PARTICIPANTLEFT_NAME_PARAM, String.class);
    ParticipantLeftInfo eventInfo = new ParticipantLeftInfo(name);
    log.debug("Recvd participant left event {}", eventInfo);
    return eventInfo;
  }

  private Notification participantJoined(Transaction transaction, Request<JsonObject> request) {
    String id = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.PARTICIPANTJOINED_USER_PARAM, String.class);
    ParticipantJoinedInfo eventInfo = new ParticipantJoinedInfo(id);
    log.debug("Recvd participant joined event {}", eventInfo);
    return eventInfo;
  }

  private Notification mediaError(Transaction transaction, Request<JsonObject> request) {
    String description = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.MEDIAERROR_ERROR_PARAM, String.class);
    MediaErrorInfo eventInfo = new MediaErrorInfo(description);
    log.debug("Recvd media error event {}", eventInfo);
    return eventInfo;
  }

  private Notification iceCandidate(Transaction transaction, Request<JsonObject> request) {

    String candidate = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.ICECANDIDATE_CANDIDATE_PARAM, String.class);
    String sdpMid = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.ICECANDIDATE_SDPMID_PARAM, String.class);
    int sdpMLineIndex = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.ICECANDIDATE_SDPMLINEINDEX_PARAM, Integer.class);

    IceCandidate iceCandidate = new IceCandidate(candidate, sdpMid, sdpMLineIndex);

    String endpoint = JsonRoomUtils.getRequestParam(request,
        ProtocolElements.ICECANDIDATE_EPNAME_PARAM, String.class);

    IceCandidateInfo eventInfo = new IceCandidateInfo(iceCandidate, endpoint);
    log.debug("Recvd ICE candidate event {}", eventInfo);

    return eventInfo;
  }

  /**
   * Blocks until an element is available and then returns it by removing it from the queue.
   *
   * @return a {@link Notification} from the queue, null when interrupted
   * @see BlockingQueue#take()
   */
  public Notification getNotification() {
    try {
      Notification notif = notifications.take();
      log.debug("Dequeued notification {}", notif);
      return notif;
    } catch (InterruptedException e) {
      log.info("Interrupted while polling notifications' queue");
      return null;
    }
  }
}
