import ServiceSelectCard from "../components/service/ServiceSelectCard";

// Renders the user service selection page.
function ServiceSelectPage() {
    return (
        <section className="service-grid">
            <ServiceSelectCard
                to="/community"
                label="커뮤니티"
                title="Community"
            />
            <ServiceSelectCard
                to="/stock"
                label="스톡"
                title="Stock"
            />
        </section>
    );
}

export default ServiceSelectPage;
