type MessageSectionProps = {
    message: string
}

function MessageSection({ message }: MessageSectionProps) {
    return (
        <section>
            <h2>Message</h2>
            <pre>{message || 'No message'}</pre>
        </section>
    )
}

export default MessageSection