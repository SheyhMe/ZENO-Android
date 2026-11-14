-- Cover the composite foreign key from messages to conversations.
create index if not exists messages_conversation_owner_idx
  on public.messages(conversation_id, user_id);
