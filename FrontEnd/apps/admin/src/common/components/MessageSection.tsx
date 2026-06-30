type MessageSectionProps = {
    message: string
}

function MessageSection({ message }: MessageSectionProps) {
    if (!message) {
        return null
    }

    return <p className="admin-message admin-section-message">{message}</p>
}

export default MessageSection
