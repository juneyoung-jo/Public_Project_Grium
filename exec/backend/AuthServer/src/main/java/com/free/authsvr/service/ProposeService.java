package com.free.authsvr.service;

import com.free.authsvr.payload.response.propose.ProposeForResponse;
import com.free.authsvr.entity.ProposeRelation;
import com.free.authsvr.entity.User;
import com.free.authsvr.entity.key.ProposeId;
import com.free.authsvr.payload.request.relation.RelationRequest;
import com.free.authsvr.repository.ProposeRepository;
import com.free.authsvr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProposeService{

    private final ProposeRepository proposeRepository;
    private final UserRepository userRepository;

    public ProposeRelation getProposeRelation(UUID userA, UUID userB) {
        ProposeId ids = ProposeId.builder()
                .senderUid(userA)
                .receiverUid(userB)
                .build();
        return proposeRepository.findById(ids).orElse(null);
    }

    public List<ProposeForResponse> getProposes(UUID receiverUid){
        List<ProposeRelation> proposeRelationList = proposeRepository.findAllByReceiverUid(receiverUid);
        return proposeRelationList.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public ProposeForResponse convertToResponse(ProposeRelation propose){
        Optional<User> userOptional = userRepository.findById(propose.getSenderUid());
        if(userOptional.isPresent()) {
            User user = userOptional.get();
            return ProposeForResponse.builder()
                    .relationId(propose.hashCode())
                    .senderName(user.getName())
                    .receiverToSender(propose.getReceiverToSender())
                    .senderToReceiver(propose.getSenderToReceiver())
                    .senderUid(propose.getSenderUid())
                    .senderImageUrl(user.getImageUrl())
                    .build()
                    ;
        }
        return null;
    }

    public boolean addPropose(RelationRequest req) { // ???????????? ??????
        // ??? ?????? ?????? ???????????? ???????????? ??????
        System.out.println(req.getSenderId().toString());
        if (!userRepository.findById(req.getSenderId()).isPresent() || !userRepository.findById(req.getReceiverId()).isPresent()) {
            return false;
        }
        // ????????? ??????
        ProposeId ids = ProposeId.builder()
                .senderUid(req.getSenderId())
                .receiverUid(req.getReceiverId())
                .build();

        // ??????
        proposeRepository.save(
                ProposeRelation.builder()
                        .ids(ids)
                        .senderToReceiver(req.getSenderToReceiver())
                        .receiverToSender(req.getReceiverToSender())
                        .build()
        );
        return true;
    }

    public boolean checkCrossPropose(UUID userA, UUID userB) { // ?????? ?????? ??????
        // ?????? ????????? ????????? ????????? ??????
        ProposeId AtoB = ProposeId.builder() // sender to receiver , ??? ????????? ???????????? ??????????
                .senderUid(userA)
                .receiverUid(userB)
                .build();

        ProposeId BtoA = ProposeId.builder() // receiver to sender
                .senderUid(userB)
                .receiverUid(userA)
                .build();

        Optional<ProposeRelation> AtoBOptional = proposeRepository.findById(AtoB);
        Optional<ProposeRelation> BtoAOptional = proposeRepository.findById(BtoA);

        return AtoBOptional.isPresent() && BtoAOptional.isPresent();

//        if(isChecked){ // ?????? ?????? ?????? ??????
//            proposeRepository.delete(AtoBOptional.get());
//            proposeRepository.delete(BtoAOptional.get());
//        }
//        return isChecked;
    }


}
