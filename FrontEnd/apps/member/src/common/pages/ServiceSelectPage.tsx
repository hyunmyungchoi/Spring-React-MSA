import ServiceSelectCard from "../components/service/ServiceSelectCard";

// Renders the member service selection page.
function ServiceSelectPage() {
    return (
        <section className="service-grid">
            <ServiceSelectCard
                to="/community"
                label="Member service"
                title="Community"
            />
            <ServiceSelectCard
                to="/stock"
                label="Member service"
                title="Stock"
            />
        </section>
    );
}

export default ServiceSelectPage;
