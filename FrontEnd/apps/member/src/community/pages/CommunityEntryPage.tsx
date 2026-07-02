import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
    createCommunityPost,
    deleteCommunityPost,
    fetchCommunityPosts,
    updateCommunityPost,
} from "../api/communityApi";
import type { CommunityPost } from "../api/communityApi";

// Renders the community CRUD workspace.
function CommunityEntryPage() {
    const [posts, setPosts] = useState<CommunityPost[]>([]);
    const [selectedPostId, setSelectedPostId] = useState<number | null>(null);
    const [title, setTitle] = useState("");
    const [content, setContent] = useState("");
    const [message, setMessage] = useState("");

    const loadPosts = async () => {
        const data = await fetchCommunityPosts();
        setPosts(data);
    };

    useEffect(() => {
        void loadPosts().catch((error) => {
            setMessage(error instanceof Error ? error.message : "Community posts load failed");
        });
    }, []);

    const resetForm = () => {
        setSelectedPostId(null);
        setTitle("");
        setContent("");
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setMessage("");

        try {
            if (selectedPostId === null) {
                await createCommunityPost({ title, content });
                setMessage("Community post created.");
            } else {
                await updateCommunityPost(selectedPostId, { title, content });
                setMessage("Community post updated.");
            }

            resetForm();
            await loadPosts();
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "Community post save failed");
        }
    };

    const handleEdit = (post: CommunityPost) => {
        setSelectedPostId(post.id);
        setTitle(post.title);
        setContent(post.content);
    };

    const handleDelete = async (postId: number) => {
        setMessage("");

        try {
            await deleteCommunityPost(postId);
            setMessage("Community post deleted.");
            if (selectedPostId === postId) {
                resetForm();
            }
            await loadPosts();
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "Community post delete failed");
        }
    };

    return (
        <section className="info-panel">
            <h2>Community CRUD</h2>
            <form className="auth-form" onSubmit={handleSubmit}>
                <label>
                    Title
                    <input value={title} onChange={(event) => setTitle(event.target.value)} required />
                </label>
                <label>
                    Content
                    <input value={content} onChange={(event) => setContent(event.target.value)} required />
                </label>
                <button className="primary-button" type="submit">
                    {selectedPostId === null ? "Create post" : "Update post"}
                </button>
                {selectedPostId !== null && (
                    <button className="secondary-button" type="button" onClick={resetForm}>
                        Cancel edit
                    </button>
                )}
            </form>

            {message && <p className="status-message">{message}</p>}

            <div className="service-grid">
                {posts.map((post) => (
                    <article className="service-tile" key={post.id}>
                        <span>{post.author}</span>
                        <strong>{post.title}</strong>
                        <p className="entry-copy">{post.content}</p>
                        <div className="user-nav">
                            <button type="button" onClick={() => handleEdit(post)}>
                                Edit
                            </button>
                            <button type="button" onClick={() => void handleDelete(post.id)}>
                                Delete
                            </button>
                        </div>
                    </article>
                ))}
            </div>
        </section>
    );
}

export default CommunityEntryPage;
